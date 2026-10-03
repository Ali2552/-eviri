package com.example.service

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

/**
 * TextToSpeech (TTS) ile Türkçe dublaj seslendirmesi yöneticisi.
 *
 * KRİTİK: Video oynatmayı durdurmamak için:
 * - Kesinlikle AudioManager audio focus istenmez (requestAudioFocus çağrılmaz).
 * - Orijinal video sesi kesintisiz ve duraksamadan çalmaya devam eder.
 * - TTS yalnızca kullanıcı Dublaj modunu seçtiğinde, odak talep etmeden çalıştırılır.
 */
class DubbingManager(
    private val context: Context,
    private val onInitComplete: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("tr", "TR"))
            isTtsReady = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED

            try {
                // Audio focus İSTEMEYEN erişilebilirlik ve yardımcı ses özniteliği
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                tts?.setAudioAttributes(audioAttributes)
            } catch (_: Exception) {}

            setupProgressListener()
            onInitComplete(isTtsReady)
        } else {
            isTtsReady = false
            onInitComplete(false)
        }
    }

    private fun setupProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {}
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {}
            override fun onError(utteranceId: String?, errorCode: Int) {}
        })
    }

    /**
     * Türkçe metni seslendirir (Audio focus istemeden).
     */
    fun speakDubbing(text: String) {
        if (!isTtsReady || text.isBlank()) return
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
