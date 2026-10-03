package com.example.translation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Alt yazıların 2 satırı geçmeyecek şekilde akışını yöneten,
 * kısmi (partial) ve nihai çevirileri birleştiren tampon sınıfı.
 */
object LiveSubtitleBuffer {

    private val _currentSubtitle = MutableStateFlow("Alt yazı servisi bekleniyor...")
    val currentSubtitle: StateFlow<String> = _currentSubtitle.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _detectedLanguage = MutableStateFlow("Otomatik")
    val detectedLanguage: StateFlow<String> = _detectedLanguage.asStateFlow()

    private val _isServiceRunning = MutableStateFlow(false)
    val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

    // Canlı ses seviyesi göstergesi (0.0f .. 1.0f)
    private val _liveAudioLevel = MutableStateFlow(0.0f)
    val liveAudioLevel: StateFlow<Float> = _liveAudioLevel.asStateFlow()

    // Tüm oturum boyunca çevrilmiş cümlelerin geçmişi (Notlara aktarmak için)
    private val _sessionHistory = MutableStateFlow<List<String>>(emptyList())
    val sessionHistory: StateFlow<List<String>> = _sessionHistory.asStateFlow()

    fun setServiceRunning(running: Boolean) {
        _isServiceRunning.value = running
        if (!running) {
            _currentSubtitle.value = "Çeviri durduruldu."
            _liveAudioLevel.value = 0.0f
        }
    }

    fun setDetectedLanguage(lang: String) {
        _detectedLanguage.value = lang
    }

    fun updateLiveAudioLevel(level: Float) {
        _liveAudioLevel.value = level.coerceIn(0.0f, 1.0f)
    }

    /**
     * Kısmi (anlık) konuşma/çeviri metni
     */
    fun updatePartial(text: String) {
        if (text.isBlank()) return
        _partialText.value = text
        updateDisplay(text, isFinal = false)
    }

    /**
     * Cümle veya parça tamamlandığında çağrılır
     */
    fun commitFinal(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _partialText.value = ""

        val updated = _sessionHistory.value.toMutableList()
        updated.add(trimmed)
        _sessionHistory.value = updated

        updateDisplay(trimmed, isFinal = true)
    }

    private fun updateDisplay(text: String, isFinal: Boolean) {
        val lines = text.split("\n").filter { it.isNotBlank() }
        val displayText = if (lines.size > 2) {
            lines.takeLast(2).joinToString("\n")
        } else if (text.length > 90) {
            val words = text.split(" ")
            val mid = words.size / 2
            val line1 = words.take(mid).joinToString(" ")
            val line2 = words.drop(mid).joinToString(" ")
            "$line1\n$line2"
        } else {
            text
        }
        _currentSubtitle.value = displayText
    }

    fun clearHistory() {
        _sessionHistory.value = emptyList()
        _currentSubtitle.value = "Yeni oturum hazır."
        _partialText.value = ""
        _liveAudioLevel.value = 0.0f
    }

    fun getAllTextForNote(): String {
        val list = _sessionHistory.value
        return if (list.isNotEmpty()) {
            list.joinToString("\n\n")
        } else {
            _currentSubtitle.value
        }
    }
}
