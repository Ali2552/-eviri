package com.example.translation

interface TranslationEngine {
    val name: String
    suspend fun translateSpeechText(recognizedText: String, sourceLangHint: String? = null): Result<String>
    suspend fun processAudioChunk(pcmData: ByteArray, sampleRate: Int): Result<String>
    fun release()
}
