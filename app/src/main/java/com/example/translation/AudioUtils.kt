package com.example.translation

object AudioUtils {

    /**
     * 16 kHz Mono 16-bit PCM ses verisini standart 44-byte başlıklı WAV formatına dönüştürür.
     */
    fun pcmToWav(
        pcmData: ByteArray,
        sampleRate: Int = 16000,
        channels: Int = 1,
        bitsPerSample: Int = 16
    ): ByteArray {
        val totalAudioLen = pcmData.size
        val totalDataLen = totalAudioLen + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8

        val header = ByteArray(44)
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // PCM chunk boyutu
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // Format 1 = PCM
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = ((sampleRate shr 8) and 0xff).toByte()
        header[26] = ((sampleRate shr 16) and 0xff).toByte()
        header[27] = ((sampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte()
        header[33] = 0
        header[34] = bitsPerSample.toByte()
        header[35] = 0
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()

        val wavData = ByteArray(44 + totalAudioLen)
        System.arraycopy(header, 0, wavData, 0, 44)
        System.arraycopy(pcmData, 0, wavData, 44, totalAudioLen)
        return wavData
    }

    /**
     * 16-bit PCM buffer için RMS enerji seviyesini hesaplar.
     */
    fun calculateRms(buffer: ShortArray, readSize: Int): Double {
        if (readSize <= 0) return 0.0
        var sum = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return Math.sqrt(sum / readSize)
    }

    /**
     * Buffer içerisindeki en yüksek (peak) mutlak genliği bulur.
     */
    fun calculatePeakAmplitude(buffer: ShortArray, readSize: Int): Int {
        var peak = 0
        for (i in 0 until readSize) {
            val absVal = Math.abs(buffer[i].toInt())
            if (absVal > peak) {
                peak = absVal
            }
        }
        return peak
    }

    /**
     * 44.1 kHz Stereo 16-bit PCM ses verisini 16 kHz Mono 16-bit PCM'e dönüştürür.
     */
    fun convert44100StereoTo16000Mono(stereo44100: ShortArray, numSamples: Int): ShortArray {
        val numFrames = numSamples / 2
        if (numFrames <= 0) return ShortArray(0)

        // 1. Stereo -> Mono birleştirme
        val mono44100 = ShortArray(numFrames)
        for (i in 0 until numFrames) {
            val left = stereo44100[i * 2].toInt()
            val right = stereo44100[i * 2 + 1].toInt()
            mono44100[i] = ((left + right) / 2).toShort()
        }

        // 2. 44100 Hz -> 16000 Hz yeniden örnekleme (Linear Interpolation)
        val targetFrames = (numFrames * 16000L / 44100L).toInt()
        val mono16000 = ShortArray(targetFrames)
        val ratio = 44100.0 / 16000.0

        for (i in 0 until targetFrames) {
            val srcIndex = i * ratio
            val indexFloor = srcIndex.toInt()
            val frac = (srcIndex - indexFloor).toFloat()

            val s1 = mono44100[indexFloor.coerceIn(0, numFrames - 1)].toFloat()
            val s2 = mono44100[(indexFloor + 1).coerceIn(0, numFrames - 1)].toFloat()
            val interpolated = s1 + frac * (s2 - s1)
            mono16000[i] = interpolated.toInt().coerceIn(-32768, 32767).toShort()
        }

        return mono16000
    }
}
