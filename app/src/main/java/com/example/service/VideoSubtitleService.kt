package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AudioSourceType
import com.example.data.preferences.EngineType
import com.example.data.preferences.SubtitleMode
import com.example.translation.GeminiTranslationEngine
import com.example.translation.LiveSubtitleBuffer
import com.example.translation.OnDeviceTranslationEngine
import com.example.translation.TranslationEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Canlı video alt yazı ve dublaj işlemlerini yürüten Ön Plan Servisi (Foreground Service).
 *
 * ANDROID 12 / 13 / 14 KURALLARI:
 * 1. Bildirim kanalı servis başlamadan önce oluşturulur (createNotificationChannel).
 * 2. Sıra: Activity sonucu -> startForegroundService -> serviste hemen startForeground() -> getMediaProjection -> registerCallback.
 * 3. Android 13 bildirim kaydırma ve Etkin Uygulamalar listesinden kapatma için onDestroy, onTaskRemoved ve onStop yönetimi.
 * 4. Tüm PendingIntent'lerde FLAG_IMMUTABLE zorunlu.
 */
class VideoSubtitleService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var preferences: AppPreferences

    private var audioCaptureManager: AudioCaptureManager? = null
    private var floatingOverlay: FloatingSubtitleView? = null
    private var dubbingManager: DubbingManager? = null

    private var geminiEngine: GeminiTranslationEngine? = null
    private var onDeviceEngine: OnDeviceTranslationEngine? = null
    private var mediaProjection: MediaProjection? = null

    companion object {
        const val ACTION_START = "com.example.service.ACTION_START"
        const val ACTION_STOP = "com.example.service.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        const val NOTIFICATION_CHANNEL_ID = "video_subtitle_channel"
        private const val NOTIFICATION_ID = 1001

        var mediaProjectionData: Intent? = null
        var mediaProjectionResultCode: Int = 0

        /**
         * Servis başlamadan önce bildirim kanalını oluşturur.
         */
        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Video Alt Yazı Servisi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Canlı alt yazı ve dublaj durumu"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferences = AppPreferences(applicationContext)
        createNotificationChannel(applicationContext)

        geminiEngine = GeminiTranslationEngine { preferences.getGeminiApiKey() }
        onDeviceEngine = OnDeviceTranslationEngine(applicationContext)
        dubbingManager = DubbingManager(applicationContext) { _ -> }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSubtitleService()
                stopSelf()
            }
            ACTION_START, null -> {
                val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, mediaProjectionResultCode) ?: mediaProjectionResultCode
                val resultData = intent?.getParcelableExtra<Intent>(EXTRA_RESULT_DATA) ?: mediaProjectionData

                val audioSource = preferences.getAudioSource()
                // 1. ÖNCE startForeground çağrılmalıdır!
                startForegroundImmediate(audioSource)

                // 2. HEMEN ARDINDAN MediaProjection ve diğer bileşenler başlatılır
                startProcessing(resultCode, resultData)
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundImmediate(audioSource: AudioSourceType) {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, VideoSubtitleService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Video Alt Yazı Aktif")
            .setContentText("Dahili video sesi dinleniyor ve Türkçe alt yazı üretiliyor...")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openPendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Durdur", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        // Android 14+ foregroundServiceType
        var foregroundType = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        if (audioSource == AudioSourceType.MICROPHONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            foregroundType = foregroundType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }

        startForeground(NOTIFICATION_ID, notification, foregroundType)
    }

    private fun startProcessing(resultCode: Int, resultData: Intent?) {
        val mode = preferences.getSubtitleMode()
        val audioSource = preferences.getAudioSource()

        if (audioSource == AudioSourceType.MEDIA_PROJECTION && resultData != null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            try {
                mediaProjection = mpManager.getMediaProjection(resultCode, resultData)
            } catch (_: Exception) {}
        }

        // Overlay penceresi (Sadece Alt Yazı veya Her İkisi modunda)
        if (mode == SubtitleMode.SUBTITLE_ONLY || mode == SubtitleMode.BOTH) {
            floatingOverlay = FloatingSubtitleView(
                context = applicationContext,
                preferences = preferences,
                onCloseClicked = {
                    stopSubtitleService()
                    stopSelf()
                }
            ).apply {
                show()
            }
        }

        LiveSubtitleBuffer.setServiceRunning(true)
        LiveSubtitleBuffer.updatePartial("Dahili video sesi dinleniyor...")

        // Ses yakalamayı başlat
        audioCaptureManager = AudioCaptureManager(
            context = applicationContext,
            mediaProjection = mediaProjection,
            audioSourceType = audioSource,
            onAudioChunkReady = { pcmChunk ->
                handleAudioChunk(pcmChunk)
            },
            onZeroAudioDetected = {
                LiveSubtitleBuffer.updatePartial("⚠️ Bu uygulama ses yakalamaya izin vermiyor")
            },
            onError = { errorMsg ->
                LiveSubtitleBuffer.updatePartial("⚠️ $errorMsg")
            },
            onMediaProjectionStopped = {
                stopSubtitleService()
                stopSelf()
            }
        ).apply {
            startCapture()
        }
    }

    private fun handleAudioChunk(pcmChunk: ByteArray) {
        val engine: TranslationEngine = when (preferences.getEngineType()) {
            EngineType.GEMINI -> geminiEngine ?: onDeviceEngine!!
            EngineType.ON_DEVICE -> onDeviceEngine!!
        }

        serviceScope.launch(Dispatchers.IO) {
            val result = engine.processAudioChunk(pcmChunk, 16000)
            result.onSuccess { translatedText ->
                val clean = translatedText.trim()
                if (clean.isNotBlank()) {
                    LiveSubtitleBuffer.commitFinal(clean)

                    val mode = preferences.getSubtitleMode()
                    if (mode == SubtitleMode.DUBBING_ONLY || mode == SubtitleMode.BOTH) {
                        dubbingManager?.speakDubbing(clean)
                    }
                }
            }.onFailure { err ->
                val msg = err.localizedMessage ?: "Çeviri hatası"
                if (!msg.contains("SocketClosed", ignoreCase = true)) {
                    LiveSubtitleBuffer.updatePartial("⚠️ $msg")
                }
            }
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Android 13'te kullanıcı uygulamayı görev yöneticisinden kaldırdığında temizlik yap
        stopSubtitleService()
        stopSelf()
    }

    private fun stopSubtitleService() {
        LiveSubtitleBuffer.setServiceRunning(false)

        audioCaptureManager?.stopCapture()
        audioCaptureManager = null

        floatingOverlay?.hide()
        floatingOverlay = null

        dubbingManager?.stop()

        try {
            mediaProjection?.stop()
        } catch (_: Exception) {}
        mediaProjection = null
    }

    override fun onDestroy() {
        stopSubtitleService()
        dubbingManager?.release()
        dubbingManager = null
        geminiEngine?.release()
        onDeviceEngine?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
