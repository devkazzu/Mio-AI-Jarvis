package com.mio.ai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mioDataStore by preferencesDataStore(name = "mio_settings")

/** Snapshot of every user-tunable setting. String enums stay UI-agnostic on purpose. */
data class MioSettings(
    val nickname: String? = null,
    // Voice
    val voiceReplies: Boolean = true,
    val speechRate: Float = 1.0f,
    val speechPitch: Float = 1.0f,
    val ttsVoiceName: String = "",
    val listeningMode: String = ListeningMode.TAP,
    // Behavior
    val confirmations: Boolean = true,
    val wakeWord: Boolean = false,
    // Background assistant (foreground service + floating orb)
    val backgroundAssistant: Boolean = false,
    // AI
    val useCloudAi: Boolean = true,
    val aiBaseUrlOverride: String = "",
    val aiModelOverride: String = "",
    val responseStyle: String = ResponseStyle.BALANCED,
    val memoryEnabled: Boolean = true,
    val historyDepth: Int = 10,
    // Automation
    val actionTimeoutSec: Int = 12,
    // Appearance
    val theme: String = MioThemePref.MIDNIGHT,
    val accentIntensity: Float = 0.85f,
    val animation: String = AnimationPref.FULL,
    // Privacy
    val keepHistory: Boolean = true,
) {
    /** Legacy v1 key migrated into [animation]; kept readable, never written. */
    val reduceMotionLegacy: Boolean get() = animation == AnimationPref.OFF
}

/** String-enum namespaces (kept in data layer so UI maps them to real enums). */
object ResponseStyle {
    const val CONCISE = "concise"
    const val BALANCED = "balanced"
    const val DETAILED = "detailed"
    fun maxTokens(style: String): Int = when (style) {
        CONCISE -> 140
        DETAILED -> 800
        else -> 450
    }

    fun instruction(style: String): String = when (style) {
        CONCISE -> "Reply in ONE short sentence. No preamble."
        DETAILED -> "You may use up to three sentences and include helpful detail."
        else -> "Keep replies to one or two short sentences."
    }
}

object ListeningMode {
    const val TAP = "tap"
    const val CONTINUOUS = "continuous"
}

object MioThemePref {
    const val MIDNIGHT = "midnight"
    const val ABYSS = "abyss"
}

object AnimationPref {
    const val FULL = "full"
    const val REDUCED = "reduced"
    const val OFF = "off"
}

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
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val LISTEN_MODE = stringPreferencesKey("listening_mode")
        val CONFIRM = booleanPreferencesKey("confirmations")
        val WAKE = booleanPreferencesKey("wake_word")
        val BG_ASSISTANT = booleanPreferencesKey("background_assistant")
        val CLOUD = booleanPreferencesKey("use_cloud_ai")
        val URL = stringPreferencesKey("ai_base_url")
        val MODEL = stringPreferencesKey("ai_model")
        val STYLE = stringPreferencesKey("response_style")
        val MEMORY = booleanPreferencesKey("memory_enabled")
        val DEPTH = intPreferencesKey("history_depth")
        val TIMEOUT = intPreferencesKey("action_timeout_sec")
        val THEME = stringPreferencesKey("theme")
        val ACCENT = floatPreferencesKey("accent_intensity")
        val ANIM = stringPreferencesKey("animation")
        val MOTION_LEGACY = booleanPreferencesKey("reduce_motion")
        val KEEP_HISTORY = booleanPreferencesKey("keep_history")
    }

    val settings: Flow<MioSettings> = context.mioDataStore.data.map { p ->
        MioSettings(
            nickname = p[Keys.NICKNAME]?.takeIf { it.isNotBlank() },
            voiceReplies = p[Keys.VOICE] ?: true,
            speechRate = (p[Keys.RATE] ?: 1.0f).coerceIn(0.5f, 2.0f),
            speechPitch = (p[Keys.PITCH] ?: 1.0f).coerceIn(0.5f, 2.0f),
            ttsVoiceName = p[Keys.TTS_VOICE].orEmpty(),
            listeningMode = p[Keys.LISTEN_MODE]?.takeIf { it in setOf(ListeningMode.TAP, ListeningMode.CONTINUOUS) }
                ?: ListeningMode.TAP,
            confirmations = p[Keys.CONFIRM] ?: true,
            wakeWord = p[Keys.WAKE] ?: false,
            backgroundAssistant = p[Keys.BG_ASSISTANT] ?: false,
            useCloudAi = p[Keys.CLOUD] ?: true,
            aiBaseUrlOverride = p[Keys.URL].orEmpty(),
            aiModelOverride = p[Keys.MODEL].orEmpty(),
            responseStyle = p[Keys.STYLE]?.takeIf { it in setOf(ResponseStyle.CONCISE, ResponseStyle.BALANCED, ResponseStyle.DETAILED) }
                ?: ResponseStyle.BALANCED,
            memoryEnabled = p[Keys.MEMORY] ?: true,
            historyDepth = (p[Keys.DEPTH] ?: 10).coerceIn(3, 20),
            actionTimeoutSec = (p[Keys.TIMEOUT] ?: 12).coerceIn(5, 30),
            theme = p[Keys.THEME]?.takeIf { it in setOf(MioThemePref.MIDNIGHT, MioThemePref.ABYSS) }
                ?: MioThemePref.MIDNIGHT,
            accentIntensity = (p[Keys.ACCENT] ?: 0.85f).coerceIn(0.3f, 1.0f),
            animation = p[Keys.ANIM]?.takeIf { it in setOf(AnimationPref.FULL, AnimationPref.REDUCED, AnimationPref.OFF) }
            // Migrate legacy reduce-motion toggle → animation OFF.
                ?: if (p[Keys.MOTION_LEGACY] == true) AnimationPref.OFF else AnimationPref.FULL,
            keepHistory = p[Keys.KEEP_HISTORY] ?: true,
        )
    }

    suspend fun setNickname(name: String?) = context.mioDataStore.edit {
        if (name.isNullOrBlank()) it.remove(Keys.NICKNAME) else it[Keys.NICKNAME] = name.take(24)
    }

    suspend fun setVoiceReplies(on: Boolean) = context.mioDataStore.edit { it[Keys.VOICE] = on }
    suspend fun setSpeechRate(v: Float) = context.mioDataStore.edit { it[Keys.RATE] = v.coerceIn(0.5f, 2.0f) }
    suspend fun setSpeechPitch(v: Float) = context.mioDataStore.edit { it[Keys.PITCH] = v.coerceIn(0.5f, 2.0f) }
    suspend fun setTtsVoice(name: String) = context.mioDataStore.edit { it[Keys.TTS_VOICE] = name.take(120) }
    suspend fun setListeningMode(mode: String) = context.mioDataStore.edit { it[Keys.LISTEN_MODE] = mode }
    suspend fun setConfirmations(on: Boolean) = context.mioDataStore.edit { it[Keys.CONFIRM] = on }
    suspend fun setWakeWord(on: Boolean) = context.mioDataStore.edit { it[Keys.WAKE] = on }
    suspend fun setBackgroundAssistant(on: Boolean) = context.mioDataStore.edit { it[Keys.BG_ASSISTANT] = on }
    suspend fun setUseCloudAi(on: Boolean) = context.mioDataStore.edit { it[Keys.CLOUD] = on }
    suspend fun setResponseStyle(style: String) = context.mioDataStore.edit { it[Keys.STYLE] = style }
    suspend fun setMemoryEnabled(on: Boolean) = context.mioDataStore.edit { it[Keys.MEMORY] = on }
    suspend fun setHistoryDepth(n: Int) = context.mioDataStore.edit { it[Keys.DEPTH] = n.coerceIn(3, 20) }
    suspend fun setActionTimeoutSec(s: Int) = context.mioDataStore.edit { it[Keys.TIMEOUT] = s.coerceIn(5, 30) }
    suspend fun setTheme(theme: String) = context.mioDataStore.edit { it[Keys.THEME] = theme }
    suspend fun setAccentIntensity(v: Float) = context.mioDataStore.edit { it[Keys.ACCENT] = v.coerceIn(0.3f, 1.0f) }
    suspend fun setAnimation(anim: String) = context.mioDataStore.edit { it[Keys.ANIM] = anim }
    suspend fun setKeepHistory(on: Boolean) = context.mioDataStore.edit { it[Keys.KEEP_HISTORY] = on }

    suspend fun setAiEndpoint(baseUrl: String, model: String) = context.mioDataStore.edit {
        it[Keys.URL] = baseUrl.trim().take(200)
        it[Keys.MODEL] = model.trim().take(80)
    }
}
