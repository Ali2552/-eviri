package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.preferences.AppPreferences
import com.example.data.preferences.AudioSourceType
import com.example.data.preferences.EngineType
import com.example.data.preferences.SubtitleMode
import com.example.data.repository.NoteRepository
import com.example.service.VideoSubtitleService
import com.example.translation.LiveSubtitleBuffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TranslationViewModel(application: Application) : AndroidViewModel(application) {

    private val preferences = AppPreferences(application)
    private val noteRepository = NoteRepository(AppDatabase.getInstance(application).noteDao())

    val subtitleMode: StateFlow<SubtitleMode> = preferences.subtitleModeFlow
    val audioSource: StateFlow<AudioSourceType> = preferences.audioSourceFlow
    val engineType: StateFlow<EngineType> = preferences.engineTypeFlow
    val fontSize: StateFlow<Float> = preferences.fontSizeFlow
    val fontColor: StateFlow<Long> = preferences.fontColorFlow
    val bgOpacity: StateFlow<Float> = preferences.bgOpacityFlow

    val currentSubtitle: StateFlow<String> = LiveSubtitleBuffer.currentSubtitle
    val isServiceRunning: StateFlow<Boolean> = LiveSubtitleBuffer.isServiceRunning
    val detectedLanguage: StateFlow<String> = LiveSubtitleBuffer.detectedLanguage
    val sessionHistory: StateFlow<List<String>> = LiveSubtitleBuffer.sessionHistory
    val liveAudioLevel: StateFlow<Float> = LiveSubtitleBuffer.liveAudioLevel

    private val _apiKeyInput = MutableStateFlow(preferences.getGeminiApiKey())
    val apiKeyInput: StateFlow<String> = _apiKeyInput.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _showNotificationPermissionWarning = MutableStateFlow(false)
    val showNotificationPermissionWarning: StateFlow<Boolean> = _showNotificationPermissionWarning.asStateFlow()

    fun updateApiKeyInput(key: String) {
        _apiKeyInput.value = key
    }

    fun saveApiKey() {
        preferences.saveGeminiApiKey(_apiKeyInput.value)
        _userMessage.value = "Gemini API anahtarı güvenle kaydedildi."
    }

    fun setSubtitleMode(mode: SubtitleMode) {
        preferences.setSubtitleMode(mode)
    }

    fun setAudioSource(source: AudioSourceType) {
        preferences.setAudioSource(source)
    }

    fun setEngineType(engine: EngineType) {
        preferences.setEngineType(engine)
    }

    fun setFontSize(size: Float) {
        preferences.setFontSize(size)
    }

    fun setFontColor(colorHex: Long) {
        preferences.setFontColor(colorHex)
    }

    fun setBgOpacity(opacity: Float) {
        preferences.setBgOpacity(opacity)
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun setNotificationWarning(show: Boolean) {
        _showNotificationPermissionWarning.value = show
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                getApplication(),
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(getApplication())
    }

    fun openAppNotificationSettings(context: Context) {
        val intent = Intent().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.parse("package:${context.packageName}")
            }
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun requestOverlayPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun startService(resultCode: Int? = null, resultData: Intent? = null) {
        val context = getApplication<Application>()
        if (resultData != null && resultCode != null) {
            VideoSubtitleService.mediaProjectionResultCode = resultCode
            VideoSubtitleService.mediaProjectionData = resultData
        }

        // Kanalı servis başlamadan önce oluştur (Android 13 kuralı)
        VideoSubtitleService.createNotificationChannel(context)

        val intent = Intent(context, VideoSubtitleService::class.java).apply {
            action = VideoSubtitleService.ACTION_START
            if (resultCode != null) putExtra(VideoSubtitleService.EXTRA_RESULT_CODE, resultCode)
            if (resultData != null) putExtra(VideoSubtitleService.EXTRA_RESULT_DATA, resultData)
        }

        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                _userMessage.value = "Uygulama arka plandayken servis başlatılamaz. Lütfen uygulamayı açarak tekrar deneyin."
            } else {
                _userMessage.value = "Servis başlatılamadı: ${e.localizedMessage}"
            }
        }
    }

    fun stopService() {
        val context = getApplication<Application>()
        val intent = Intent(context, VideoSubtitleService::class.java).apply {
            action = VideoSubtitleService.ACTION_STOP
        }
        try {
            context.startService(intent)
        } catch (_: Exception) {}
    }

    fun saveCurrentSubtitleToNotes(onSaved: () -> Unit) {
        val textToSave = LiveSubtitleBuffer.getAllTextForNote()
        if (textToSave.isBlank() || textToSave == "Alt yazı servisi bekleniyor..." || textToSave == "Çeviri durduruldu.") {
            _userMessage.value = "Kaydedilecek alt yazı metni bulunmuyor."
            return
        }

        viewModelScope.launch {
            val title = "Video Çeviri Notu - ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
            noteRepository.insertNote(title, textToSave)
            _userMessage.value = "Alt yazı başarıyla Notlar'a kaydedildi!"
            onSaved()
        }
    }

    fun clearHistory() {
        LiveSubtitleBuffer.clearHistory()
        _userMessage.value = "Alt yazı geçmişi temizlendi."
    }
}
