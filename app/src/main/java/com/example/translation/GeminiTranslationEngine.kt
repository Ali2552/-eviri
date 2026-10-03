package com.example.translation

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiTranslationEngine(
    private val apiKeyProvider: () -> String
) : TranslationEngine {

    override val name: String = "Gemini Cloud API (Canlı Ses Çeviri)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(6, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Doğrudan PCM ses verisini alıp Gemini API'ye WAV formatında ileterek Türkçe alt yazı üretir.
     * Sistem talimatı:
     * "Sana ses verilecek. Dili otomatik algıla. Türkçe değilse Türkçeye çevir, Türkçeyse aynen yaz. Sadece alt yazı metnini döndür, başka açıklama ekleme."
     */
    override suspend fun processAudioChunk(pcmData: ByteArray, sampleRate: Int): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API anahtarı girilmemiş. Lütfen Ayarlar bölümünden anahtarınızı kaydedin.")
            )
        }

        if (pcmData.isEmpty()) {
            return@withContext Result.success("")
        }

        try {
            val wavBytes = AudioUtils.pcmToWav(pcmData, sampleRate)
            val base64Audio = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"

            val systemInstructionText = "Sana ses verilecek. Dili otomatik algıla. Türkçe değilse Türkçeye çevir, Türkçeyse aynen yaz. Sadece alt yazı metnini döndür, başka açıklama ekleme."

            val requestBodyJson = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", systemInstructionText))
                    })
                })
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "audio/wav")
                                    put("data", base64Audio)
                                })
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.0)
                    put("maxOutputTokens", 80)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    Exception("Gemini API (${response.code}): $responseBody")
                )
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val translatedText = parts.getJSONObject(0).optString("text", "").trim()
                    return@withContext Result.success(translatedText)
                }
            }

            Result.success("")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun translateSpeechText(
        recognizedText: String,
        sourceLangHint: String?
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Gemini API anahtarı girilmemiş.")
            )
        }

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey"
            val prompt = "Aşağıdaki metni Türkçeye çevir (Türkçeyse aynen yaz). Sadece alt yazıyı döndür, en fazla 2 satır olsun: \"$recognizedText\""

            val requestBodyJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", prompt))
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 80)
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Gemini Hatası (${response.code})"))
            }

            val json = JSONObject(responseBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val candidate = candidates.getJSONObject(0)
                val content = candidate.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val translated = parts.getJSONObject(0).optString("text", "").trim()
                    return@withContext Result.success(translated)
                }
            }

            Result.success(recognizedText)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun release() {}
}
