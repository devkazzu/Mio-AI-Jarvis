package com.mio.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Encrypted on-device storage for the optional in-app API-key override.
 * Backed by Android Keystore (AES256-GCM master key) via
 * EncryptedSharedPreferences — key material never touches disk in cleartext.
 *
 * The stored key is write-only from the UI's perspective: it is read only to
 * build the `Authorization` header, never for display, logging, or diagnostics.
 *
 * Priority for the cloud brain credentials:
 *  1. In-app override (this store) — set in Settings → AI.
 *  2. BuildConfig values from local.properties (developer machine).
 */
class SecureKeyStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
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
