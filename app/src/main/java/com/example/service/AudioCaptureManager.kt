package com.example.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.data.preferences.AudioSourceType
import com.example.translation.AudioUtils
import com.example.translation.LiveSubtitleBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Android 10-14 uyumlu, audio focus talep etmeyen, yüksek kaliteli dahili ses yakalama yöneticisi.
 *
 * ÖZELLİKLER:
 * 1. 44100 Hz Stereo yakalama ve 16 kHz Mono'ya dönüştürme (Fallback: doğrudan 16 kHz Mono).
 * 2. AudioRecord STATE_INITIALIZED kontrolü ve RECORD_AUDIO kontrolü.
 * 3. Kesinlikle requestAudioFocus çağırmaz, video kesintisiz oynar.
 * 4. Yanlış alarm önleme: 15 sn geçtikten sonra, isMusicActive() true iken ve 10 sn boyunca peak genlik 0 ise uyarı verir.
 * 5. Canlı ses seviyesi göstergesi (VU Meter) için LiveSubtitleBuffer güncellenir.
 * 6. MediaProjection onStop dinleyicisi ile temiz kaynak yönetimi.
 */
class AudioCaptureManager(
    private val context: Context,
    private val mediaProjection: MediaProjection?,
    private val audioSourceType: AudioSourceType,
    private val onAudioChunkReady: (pcmData: ByteArray) -> Unit,
    private val onZeroAudioDetected: () -> Unit,
    private val onError: (String) -> Unit,
    private val onMediaProjectionStopped: () -> Unit
) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaProjectionCallback: MediaProjection.Callback? = null

    private var isCapturingStereo44100 = false

    @SuppressLint("MissingPermission")
    fun startCapture() {
        if (isRecording) return

        try {
            // MediaProjection onStop dinleyicisi
            if (mediaProjection != null) {
                val callback = object : MediaProjection.Callback() {
                    override fun onStop() {
                        stopCapture()
                        mainHandler.post {
                            onMediaProjectionStopped()
                        }
                    }
                }
                mediaProjectionCallback = callback
                mediaProjection.registerCallback(callback, mainHandler)
            }

            if (audioSourceType == AudioSourceType.MEDIA_PROJECTION) {
                if (mediaProjection == null) {
                    onError("Ekran/Ses yakalama yetkisi bulunamadı.")
                    return
                }

                val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                // 1. Önce 44100 Hz Stereo dene
                val sampleRateStereo = 44100
                val channelStereo = AudioFormat.CHANNEL_IN_STEREO
                val formatPcm16 = AudioFormat.ENCODING_PCM_16BIT
                val minBufferStereo = AudioRecord.getMinBufferSize(sampleRateStereo, channelStereo, formatPcm16)

                var record = AudioRecord.Builder()
                    .setAudioPlaybackCaptureConfig(playbackConfig)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(formatPcm16)
                            .setSampleRate(sampleRateStereo)
                            .setChannelMask(channelStereo)
                            .build()
                    )
                    .setBufferSizeInBytes((minBufferStereo * 2).coerceAtLeast(sampleRateStereo * 2 * 2 / 2))
                    .build()

                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    audioRecord = record
                    isCapturingStereo44100 = true
                } else {
                    record.release()
                    // Fallback: 16000 Hz Mono
                    val sampleRateMono = 16000
                    val channelMono = AudioFormat.CHANNEL_IN_MONO
                    val minBufferMono = AudioRecord.getMinBufferSize(sampleRateMono, channelMono, formatPcm16)

                    record = AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(playbackConfig)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setEncoding(formatPcm16)
                                .setSampleRate(sampleRateMono)
                                .setChannelMask(channelMono)
                                .build()
                        )
                        .setBufferSizeInBytes((minBufferMono * 2).coerceAtLeast(sampleRateMono * 2 / 2))
                        .build()

                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        audioRecord = record
                        isCapturingStereo44100 = false
                    } else {
                        record.release()
                        onError("Dahili ses yakalama aygıtı başlatılamadı.")
                        return
                    }
                }
            } else {
                // Mikrofon Yedeği (Kullanıcı seçtiğinde RECORD_AUDIO izni ile)
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                    onError("Mikrofon yedeği için ses kayıt izni verilmemiş.")
                    return
                }

                val minBuffer = AudioRecord.getMinBufferSize(
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val record = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    16000,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    (minBuffer * 2).coerceAtLeast(16000)
                )

                if (record.state != AudioRecord.STATE_INITIALIZED) {
                    record.release()
                    onError("Mikrofon donanımı başlatılamadı.")
                    return
                }
                audioRecord = record
                isCapturingStereo44100 = false
            }

            audioRecord?.startRecording()
            isRecording = true

            startAudioLoop()

        } catch (e: Exception) {
            onError("Ses yakalama başlatılırken hata: ${e.localizedMessage}")
        }
    }

    private fun startAudioLoop() {
        recordJob = scope.launch {
            val startTimeMs = System.currentTimeMillis()
            var zeroDurationMs = 0L
            var zeroAlertFired = false

            // Okuma arabellekleri
            val rawBuffer = if (isCapturingStereo44100) ShortArray(4410 * 2) else ShortArray(3200) // ~200ms
            val speechAccumulator = ByteArrayOutputStream()
            var activeSpeechFrames = 0
            var silenceFramesAfterSpeech = 0

            while (isActive && isRecording) {
                val readShorts = audioRecord?.read(rawBuffer, 0, rawBuffer.size) ?: 0
                if (readShorts <= 0) continue

                // 16 kHz Mono'ya dönüştür
                val mono16k: ShortArray = if (isCapturingStereo44100) {
                    AudioUtils.convert44100StereoTo16000Mono(rawBuffer, readShorts)
                } else {
                    rawBuffer.copyOf(readShorts)
                }

                val peak = AudioUtils.calculatePeakAmplitude(mono16k, mono16k.size)
                val rms = AudioUtils.calculateRms(mono16k, mono16k.size)

                // Canlı ses seviyesi göstergesi (VU Meter) güncelle (0.0f .. 1.0f)
                val normalizedLevel = (peak / 24000.0f).coerceIn(0.0f, 1.0f)
                LiveSubtitleBuffer.updateLiveAudioLevel(normalizedLevel)

                // 5. Yanlış "engelleniyor" alarmını önle:
                // Şartlar:
                // 1) En az 15 sn geçtiyse,
                // 2) audioManager.isMusicActive() true iken (cihazda müzik/video çalıyorsa),
                // 3) 10 sn boyunca kesintisiz peak genlik 0 ise.
                val chunkDurationMs = (mono16k.size * 1000L) / 16000L
                if (peak == 0) {
                    zeroDurationMs += chunkDurationMs
                } else {
                    zeroDurationMs = 0L
                }

                val elapsedTimeMs = System.currentTimeMillis() - startTimeMs
                val isMusicPlaying = audioManager.isMusicActive

                if (elapsedTimeMs >= 15_000L && isMusicPlaying && zeroDurationMs >= 10_000L && !zeroAlertFired && audioSourceType == AudioSourceType.MEDIA_PROJECTION) {
                    zeroAlertFired = true
                    mainHandler.post {
                        onZeroAudioDetected()
                    }
                }

                // VAD (Voice Activity Detection): RMS > 160 konuşma var demektir
                val isSpeechActive = rms > 160.0

                if (isSpeechActive) {
                    val byteBuf = ByteBuffer.allocate(mono16k.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    for (sample in mono16k) {
                        byteBuf.putShort(sample)
                    }
                    speechAccumulator.write(byteBuf.array(), 0, mono16k.size * 2)
                    activeSpeechFrames++
                    silenceFramesAfterSpeech = 0

                    // Maksimum 2.5 saniyede bir parça gönder
                    if (speechAccumulator.size() >= 16000 * 2 * 2.5) {
                        val pcmData = speechAccumulator.toByteArray()
                        speechAccumulator.reset()
                        activeSpeechFrames = 0
                        onAudioChunkReady(pcmData)
                    }
                } else {
                    if (activeSpeechFrames > 0) {
                        silenceFramesAfterSpeech++

                        // Konuşma bittiğinde ~400ms duraklama varsa parçayı sevk et
                        if (silenceFramesAfterSpeech >= 2) {
                            if (speechAccumulator.size() >= 16000 * 2 * 0.8) { // En az 800ms ses
                                val pcmData = speechAccumulator.toByteArray()
                                onAudioChunkReady(pcmData)
                            }
                            speechAccumulator.reset()
                            activeSpeechFrames = 0
                            silenceFramesAfterSpeech = 0
                        }
                    }
                }
            }
        }
    }

    fun stopCapture() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        LiveSubtitleBuffer.updateLiveAudioLevel(0.0f)

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        try {
            mediaProjectionCallback?.let {
                mediaProjection?.unregisterCallback(it)
            }
        } catch (_: Exception) {}
        mediaProjectionCallback = null
    }
}
