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
 * build the `Authorization` header, never for display, logging, or diagnostics
 * (Settings shows only [maskedHint]). It is the SOLE credential source for
 * the cloud brain — no key ever lives in source, resources, or build config.
 *
 * Honest limitation: this protects the key at rest on the user's own device.
 * Anyone the user shares a configured device (or a backup) with could use
 * the key — for a distributed app, route AI through a first-party backend
 * proxy instead (see [com.mio.ai.core.ai.AiClient]).
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

    /**
     * Display-only hint for Settings ("sk-••••1234"). The full key is never
     * exposed for display — this reveals just enough to confirm which key.
     */
    fun maskedHint(): String {
        val k = getApiKey()
        if (k.isBlank()) return "not set"
        if (k.length < 8) return "••••"
        return "${k.take(3)}••••${k.takeLast(4)}"
    }

    companion object {
        private const val KEY_API = "ai_api_key"
    }
}
