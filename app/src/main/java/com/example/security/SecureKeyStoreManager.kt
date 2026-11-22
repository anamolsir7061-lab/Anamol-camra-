package com.example.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class SecureKeyStoreManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _hasApiKey = MutableStateFlow(false)
    val hasApiKey: StateFlow<Boolean> = _hasApiKey.asStateFlow()

    private val _maskedKey = MutableStateFlow("")
    val maskedKey: StateFlow<String> = _maskedKey.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus() {
        val vaultKey = getStoredVaultKey()
        val buildConfigKey = getBuildConfigKey()
        val active = vaultKey ?: buildConfigKey
        _hasApiKey.value = !active.isNullOrBlank()
        _maskedKey.value = when {
            !vaultKey.isNullOrBlank() -> maskKey(vaultKey)
            !buildConfigKey.isNullOrBlank() -> "AI Studio Secret (${maskKey(buildConfigKey)})"
            else -> ""
        }
    }

    fun saveApiKey(rawKey: String): Boolean {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) return false
        return try {
            val secretKey = getOrCreateSecretKey()
            if (secretKey != null) {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, secretKey)
                val iv = cipher.iv
                val encrypted = cipher.doFinal(trimmed.toByteArray(StandardCharsets.UTF_8))
                prefs.edit()
                    .putString(KEY_CIPHER_BLOB, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                    .putString(KEY_CIPHER_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .remove(KEY_FALLBACK)
                    .commit()
            } else {
                // Fallback only if hardware/software AndroidKeyStore is unavailable (e.g. JVM test)
                val encoded = Base64.encodeToString(trimmed.toByteArray(StandardCharsets.UTF_8), Base64.NO_WRAP)
                prefs.edit()
                    .putString(KEY_FALLBACK, encoded)
                    .commit()
            }
            refreshStatus()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun getApiKey(): String? = getStoredVaultKey() ?: getBuildConfigKey()

    private fun getStoredVaultKey(): String? {
        return try {
            val cipherBlob = prefs.getString(KEY_CIPHER_BLOB, null)
            val ivString = prefs.getString(KEY_CIPHER_IV, null)
            if (cipherBlob != null && ivString != null) {
                val secretKey = getOrCreateSecretKey() ?: return null
                val cipher = Cipher.getInstance(TRANSFORMATION)
                val spec = GCMParameterSpec(128, Base64.decode(ivString, Base64.NO_WRAP))
                cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
                val decrypted = cipher.doFinal(Base64.decode(cipherBlob, Base64.NO_WRAP))
                String(decrypted, StandardCharsets.UTF_8)
            } else {
                val fallback = prefs.getString(KEY_FALLBACK, null) ?: return null
                String(Base64.decode(fallback, Base64.NO_WRAP), StandardCharsets.UTF_8)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getBuildConfigKey(): String? {
        val raw = runCatching { BuildConfig.GEMINI_API_KEY.trim() }.getOrDefault("")
        if (raw.isEmpty() || raw == PLACEHOLDER_KEY) return null
        return raw
    }

    fun clearApiKey() {
        prefs.edit()
            .remove(KEY_CIPHER_BLOB)
            .remove(KEY_CIPHER_IV)
            .remove(KEY_FALLBACK)
            .commit()
        refreshStatus()
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            (keyStore.getKey(KEY_ALIAS, null) as? SecretKey) ?: run {
                val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                generator.init(spec)
                generator.generateKey()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun maskKey(key: String): String {
        if (key.length <= 8) return "••••••••"
        return "${key.take(4)}••••••••••••${key.takeLast(4)}"
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_ALIAS = "lensmind_gemini_api_key_v1"
        private const val PREFS_NAME = "lensmind_secure_vault"
        private const val KEY_CIPHER_BLOB = "enc_gemini_key"
        private const val KEY_CIPHER_IV = "enc_gemini_iv"
        private const val KEY_FALLBACK = "enc_gemini_fallback"
        private const val PLACEHOLDER_KEY = "MY_GEMINI_API_KEY"
    }
}
