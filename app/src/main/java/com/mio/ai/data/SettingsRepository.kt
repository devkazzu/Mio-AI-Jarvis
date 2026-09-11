package com.mio.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mioDataStore by preferencesDataStore(name = "mio_settings")

/** Snapshot of every user-tunable setting. */
data class MioSettings(
    val nickname: String? = null,
    val voiceReplies: Boolean = true,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val confirmations: Boolean = true,
    val wakeWord: Boolean = false,
    val reduceMotion: Boolean = false,
    val useCloudAi: Boolean = true,
    val aiBaseUrlOverride: String = "",
    val aiModelOverride: String = "",
)

/**
 * DataStore-backed settings. The API key itself is NOT here — it lives in
 * encrypted storage ([SecureKeyStore]) or BuildConfig (local.properties).
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val NICKNAME = stringPreferencesKey("nickname")
        val VOICE = booleanPreferencesKey("voice_replies")
        val RATE = floatPreferencesKey("speech_rate")
        val PITCH = floatPreferencesKey("speech_pitch")
        val CONFIRM = booleanPreferencesKey("confirmations")
        val WAKE = booleanPreferencesKey("wake_word")
        val MOTION = booleanPreferencesKey("reduce_motion")
        val CLOUD = booleanPreferencesKey("use_cloud_ai")
        val URL = stringPreferencesKey("ai_base_url")
        val MODEL = stringPreferencesKey("ai_model")
    }

    val settings: Flow<MioSettings> = context.mioDataStore.data.map { p ->
        MioSettings(
            nickname = p[Keys.NICKNAME]?.takeIf { it.isNotBlank() },
            voiceReplies = p[Keys.VOICE] ?: true,
            speechRate = (p[Keys.RATE] ?: 1.0f).coerceIn(0.5f, 2.0f),
            speechPitch = (p[Keys.PITCH] ?: 1.0f).coerceIn(0.5f, 2.0f),
            confirmations = p[Keys.CONFIRM] ?: true,
            wakeWord = p[Keys.WAKE] ?: false,
            reduceMotion = p[Keys.MOTION] ?: false,
            useCloudAi = p[Keys.CLOUD] ?: true,
            aiBaseUrlOverride = p[Keys.URL].orEmpty(),
            aiModelOverride = p[Keys.MODEL].orEmpty(),
        )
    }

    suspend fun setNickname(name: String?) = context.mioDataStore.edit {
        if (name.isNullOrBlank()) it.remove(Keys.NICKNAME) else it[Keys.NICKNAME] = name.take(24)
    }

    suspend fun setVoiceReplies(on: Boolean) = context.mioDataStore.edit { it[Keys.VOICE] = on }
    suspend fun setSpeechRate(v: Float) = context.mioDataStore.edit { it[Keys.RATE] = v.coerceIn(0.5f, 2.0f) }
    suspend fun setSpeechPitch(v: Float) = context.mioDataStore.edit { it[Keys.PITCH] = v.coerceIn(0.5f, 2.0f) }
    suspend fun setConfirmations(on: Boolean) = context.mioDataStore.edit { it[Keys.CONFIRM] = on }
    suspend fun setWakeWord(on: Boolean) = context.mioDataStore.edit { it[Keys.WAKE] = on }
    suspend fun setReduceMotion(on: Boolean) = context.mioDataStore.edit { it[Keys.MOTION] = on }
    suspend fun setUseCloudAi(on: Boolean) = context.mioDataStore.edit { it[Keys.CLOUD] = on }

    suspend fun setAiEndpoint(baseUrl: String, model: String) = context.mioDataStore.edit {
        it[Keys.URL] = baseUrl.trim().take(200)
        it[Keys.MODEL] = model.trim().take(80)
    }
}
