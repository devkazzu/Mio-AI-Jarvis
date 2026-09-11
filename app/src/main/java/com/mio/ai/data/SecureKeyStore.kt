package com.mio.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted on-device storage for the optional in-app API-key override.
 * Priority for the cloud brain credentials:
 *  1. In-app override (this store) — set in Settings → AI Brain.
 *  2. BuildConfig values from local.properties (developer machine).
 */
class SecureKeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "mio_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getApiKey(): String = runCatching { prefs.getString(KEY_API, "").orEmpty() }.getOrDefault("")

    fun setApiKey(key: String) {
        runCatching { prefs.edit().putString(KEY_API, key.trim()).apply() }
    }

    fun clearApiKey() {
        runCatching { prefs.edit().remove(KEY_API).apply() }
    }

    fun hasApiKey(): Boolean = getApiKey().isNotBlank()

    companion object {
        private const val KEY_API = "ai_api_key"
    }
}
