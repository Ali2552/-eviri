package com.example.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

enum class SubtitleMode(val titleTr: String) {
    SUBTITLE_ONLY("Sadece Alt Yazı"),
    DUBBING_ONLY("Sadece Dublaj"),
    BOTH("Alt Yazı + Dublaj")
}

enum class AudioSourceType(val titleTr: String) {
    MEDIA_PROJECTION("Cihaz İçi Ses (Hoparlör)"),
    MICROPHONE("Mikrofon Yedeği")
}

enum class EngineType(val titleTr: String) {
    GEMINI("Bulut (Gemini API)"),
    ON_DEVICE("Çevrimdışı (On-Device ML)")
}

class AppPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("video_alt_yazi_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALIAS = "VideoAltYaziKeyAlias"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"

        private const val PREF_ENCRYPTED_API_KEY = "encrypted_gemini_api_key"
        private const val PREF_API_KEY_IV = "api_key_iv"
        private const val PREF_SUBTITLE_MODE = "subtitle_mode"
        private const val PREF_AUDIO_SOURCE = "audio_source"
        private const val PREF_ENGINE_TYPE = "engine_type"
        private const val PREF_FONT_SIZE = "font_size"
        private const val PREF_FONT_COLOR = "font_color"
        private const val PREF_BG_OPACITY = "bg_opacity"
        private const val PREF_DUCKING_PERCENT = "ducking_percent"
    }

    private val _subtitleModeFlow = MutableStateFlow(getSubtitleMode())
    val subtitleModeFlow: StateFlow<SubtitleMode> = _subtitleModeFlow.asStateFlow()

    private val _audioSourceFlow = MutableStateFlow(getAudioSource())
    val audioSourceFlow: StateFlow<AudioSourceType> = _audioSourceFlow.asStateFlow()

    private val _engineTypeFlow = MutableStateFlow(getEngineType())
    val engineTypeFlow: StateFlow<EngineType> = _engineTypeFlow.asStateFlow()

    private val _fontSizeFlow = MutableStateFlow(getFontSize())
    val fontSizeFlow: StateFlow<Float> = _fontSizeFlow.asStateFlow()

    private val _fontColorFlow = MutableStateFlow(getFontColor())
    val fontColorFlow: StateFlow<Long> = _fontColorFlow.asStateFlow()

    private val _bgOpacityFlow = MutableStateFlow(getBgOpacity())
    val bgOpacityFlow: StateFlow<Float> = _bgOpacityFlow.asStateFlow()

    init {
        ensureKeystoreKey()
    }

    private fun ensureKeystoreKey() {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (_: Exception) {
            // Android Keystore fallback
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (_: Exception) {
            null
        }
    }

    fun saveGeminiApiKey(apiKey: String) {
        if (apiKey.isBlank()) {
            prefs.edit().remove(PREF_ENCRYPTED_API_KEY).remove(PREF_API_KEY_IV).apply()
            return
        }
        try {
            val secretKey = getSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encryptedBytes = cipher.doFinal(apiKey.toByteArray(Charsets.UTF_8))

                prefs.edit()
                    .putString(PREF_ENCRYPTED_API_KEY, Base64.encodeToString(encryptedBytes, Base64.NO_WRAP))
                    .putString(PREF_API_KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .apply()
            } else {
                // Fallback obfuscated
                prefs.edit()
                    .putString(PREF_ENCRYPTED_API_KEY, Base64.encodeToString(apiKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                    .apply()
            }
        } catch (_: Exception) {
            prefs.edit()
                .putString(PREF_ENCRYPTED_API_KEY, Base64.encodeToString(apiKey.toByteArray(Charsets.UTF_8), Base64.NO_WRAP))
                .apply()
        }
    }

    fun getGeminiApiKey(): String {
        val encryptedBase64 = prefs.getString(PREF_ENCRYPTED_API_KEY, null) ?: return ""
        val ivBase64 = prefs.getString(PREF_API_KEY_IV, null)
        return try {
            if (ivBase64 != null) {
                val secretKey = getSecretKey()
                if (secretKey != null) {
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
                    val spec = GCMParameterSpec(128, iv)
                    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                    val decryptedBytes = cipher.doFinal(Base64.decode(encryptedBase64, Base64.NO_WRAP))
                    String(decryptedBytes, Charsets.UTF_8)
                } else {
                    String(Base64.decode(encryptedBase64, Base64.NO_WRAP), Charsets.UTF_8)
                }
            } else {
                String(Base64.decode(encryptedBase64, Base64.NO_WRAP), Charsets.UTF_8)
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun setSubtitleMode(mode: SubtitleMode) {
        prefs.edit().putString(PREF_SUBTITLE_MODE, mode.name).apply()
        _subtitleModeFlow.value = mode
    }

    fun getSubtitleMode(): SubtitleMode {
        val name = prefs.getString(PREF_SUBTITLE_MODE, SubtitleMode.SUBTITLE_ONLY.name)
        return try {
            SubtitleMode.valueOf(name ?: SubtitleMode.SUBTITLE_ONLY.name)
        } catch (_: Exception) {
            SubtitleMode.SUBTITLE_ONLY
        }
    }

    fun setAudioSource(source: AudioSourceType) {
        prefs.edit().putString(PREF_AUDIO_SOURCE, source.name).apply()
        _audioSourceFlow.value = source
    }

    fun getAudioSource(): AudioSourceType {
        val name = prefs.getString(PREF_AUDIO_SOURCE, AudioSourceType.MEDIA_PROJECTION.name)
        return try {
            AudioSourceType.valueOf(name ?: AudioSourceType.MEDIA_PROJECTION.name)
        } catch (_: Exception) {
            AudioSourceType.MEDIA_PROJECTION
        }
    }

    fun setEngineType(engine: EngineType) {
        prefs.edit().putString(PREF_ENGINE_TYPE, engine.name).apply()
        _engineTypeFlow.value = engine
    }

    fun getEngineType(): EngineType {
        val name = prefs.getString(PREF_ENGINE_TYPE, EngineType.ON_DEVICE.name)
        return try {
            EngineType.valueOf(name ?: EngineType.ON_DEVICE.name)
        } catch (_: Exception) {
            EngineType.ON_DEVICE
        }
    }

    fun setFontSize(size: Float) {
        prefs.edit().putFloat(PREF_FONT_SIZE, size).apply()
        _fontSizeFlow.value = size
    }

    fun getFontSize(): Float = prefs.getFloat(PREF_FONT_SIZE, 17f)

    fun setFontColor(colorHex: Long) {
        prefs.edit().putLong(PREF_FONT_COLOR, colorHex).apply()
        _fontColorFlow.value = colorHex
    }

    fun getFontColor(): Long = prefs.getLong(PREF_FONT_COLOR, 0xFFFFFFFF)

    fun setBgOpacity(opacity: Float) {
        prefs.edit().putFloat(PREF_BG_OPACITY, opacity).apply()
        _bgOpacityFlow.value = opacity
    }

    fun getBgOpacity(): Float = prefs.getFloat(PREF_BG_OPACITY, 0.82f)

    fun setDuckingPercent(percent: Int) {
        prefs.edit().putInt(PREF_DUCKING_PERCENT, percent.coerceIn(0, 80)).apply()
    }

    fun getDuckingPercent(): Int = prefs.getInt(PREF_DUCKING_PERCENT, 25)
}
