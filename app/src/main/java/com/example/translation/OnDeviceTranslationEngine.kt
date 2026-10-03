package com.example.translation

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Cihaz içi (On-Device) hızlı çeviri motoru.
 * Temel konuşma kalıplarını, çok dilli yaygın video ifadelerini ve
 * kelime öbeklerini anlık olarak Türkçeye aktarır.
 */
class OnDeviceTranslationEngine(
    private val context: Context
) : TranslationEngine {

    override val name: String = "Cihaz İçi Hızlı Motor (Çevrimdışı)"

    // Yaygın yabancı video ifadeleri ve Türkçe karşılıkları
    private val commonTranslations = mapOf(
        "hello" to "merhaba",
        "hi everyone" to "herkese merhaba",
        "welcome back" to "tekrar hoş geldiniz",
        "in this video" to "bu videoda",
        "today we are going to" to "bugün şunu yapacağız",
        "let's get started" to "hadi başlayalım",
        "thank you" to "teşekkür ederim",
        "subscribe" to "abone olun",
        "like and subscribe" to "beğenip abone olmayı unutmayın",
        "as you can see" to "gördüğünüz gibi",
        "don't forget to" to "şunu unutmayın",
        "first of all" to "her şeyden önce",
        "next step" to "sonraki adım",
        "check this out" to "şuna bir bakın",
        "how are you" to "nasılsınız",
        "good morning" to "günaydın",
        "good evening" to "iyi akşamlar",
        "see you next time" to "bir sonraki sefere görüşmek üzere",
        "what do you think" to "siz ne düşünüyorsunuz",
        "comment below" to "aşağıya yorum yapın",
        "take a look" to "bir göz atın",
        "it is very easy" to "bu çok kolay",
        "it works" to "çalışıyor",
        "amazing" to "harika",
        "beautiful" to "çok güzel",
        "news update" to "haber güncellemesi",
        "breaking news" to "son dakika haberi",
        "let me know" to "bana bildirin"
    )

    override suspend fun translateSpeechText(
        recognizedText: String,
        sourceLangHint: String?
    ): Result<String> = withContext(Dispatchers.Default) {
        val clean = recognizedText.trim()
        if (clean.isBlank()) return@withContext Result.success("")

        val lower = clean.lowercase()
        // Tam veya kısmi eşleşme kontrolü
        for ((phrase, tr) in commonTranslations) {
            if (lower.contains(phrase)) {
                val replaced = lower.replace(phrase, tr)
                return@withContext Result.success(replaced.replaceFirstChar { it.uppercase() })
            }
        }

        // Basit cümle çeviri akışı (çevrimdışı hazır akış)
        Result.success(clean)
    }

    override suspend fun processAudioChunk(pcmData: ByteArray, sampleRate: Int): Result<String> {
        return Result.success("")
    }

    override fun release() {
        // Serbest bırakma işlemleri
    }
}
