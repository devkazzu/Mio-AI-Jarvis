package com.mio.ai.core.ai

/**
 * Effective cloud configuration: user overrides win, build defaults fill
 * the blanks. Pure JVM code (no Android APIs) so it is unit-testable, and
 * the single place that decides what "configured" means.
 *
 * The API key is deliberately NOT part of this object — it lives only in
 * encrypted storage ([com.mio.ai.data.SecureKeyStore]) and is passed to the
 * HTTP client at request time. A future first-party proxy slots in behind
 * [AiClient] without touching any of this.
 */
data class EffectiveCloudConfig(val baseUrl: String, val model: String)

object CloudBrainConfig {

    fun effective(
        urlOverride: String,
        modelOverride: String,
        defaultBaseUrl: String,
        defaultModel: String,
    ): EffectiveCloudConfig = EffectiveCloudConfig(
        baseUrl = urlOverride.ifBlank { defaultBaseUrl }.trim(),
        model = modelOverride.ifBlank { defaultModel }.trim(),
    )

    /**
     * True when a cloud attempt makes sense: toggle on, URL + model present,
     * and a key on file unless the host is local (Ollama/LM Studio style).
     */
    fun isReady(useCloud: Boolean, effective: EffectiveCloudConfig, hasKey: Boolean): Boolean {
        if (!useCloud) return false
        if (effective.baseUrl.isBlank() || effective.model.isBlank()) return false
        if (!hasKey && BrainStatusResolver.needsApiKey(effective.baseUrl)) return false
        return true
    }
}
