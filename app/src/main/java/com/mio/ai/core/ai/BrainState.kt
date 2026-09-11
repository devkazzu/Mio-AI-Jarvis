package com.mio.ai.core.ai

/**
 * The ONE brain-selection state for the whole app. Home and Settings render
 * this same object, so they can never disagree about which brain is active.
 */
enum class BrainStatus {
    /** Cloud toggle off — local command engine + on-device replies only. */
    OFFLINE,
    /** Cloud on and fully configured — requests go to the provider. */
    CLOUD_READY,
    /** Cloud on but URL/model/key incomplete — offline handles commands. */
    CLOUD_SETUP_REQUIRED,
    /** Cloud on and configured, but the last request failed. */
    CLOUD_ERROR,
}

data class BrainState(val status: BrainStatus, val detail: String) {
    /** Short uppercase label for the Home status line. */
    val label: String = when (status) {
        BrainStatus.OFFLINE -> "OFFLINE BRAIN"
        BrainStatus.CLOUD_READY -> "CLOUD BRAIN"
        BrainStatus.CLOUD_SETUP_REQUIRED -> "CLOUD SETUP REQUIRED"
        BrainStatus.CLOUD_ERROR -> "CLOUD BRAIN ERROR"
    }
}

/**
 * Pure brain-state resolver (no Android APIs — unit-tested).
 *
 * A key is required for remote hosts; loopback and private-network hosts
 * (Ollama / LM Studio style local servers) work without one.
 */
object BrainStatusResolver {

    fun resolve(
        useCloud: Boolean,
        baseUrl: String,
        model: String,
        hasKey: Boolean,
        lastError: String?,
    ): BrainState {
        if (!useCloud) return BrainState(BrainStatus.OFFLINE, "Cloud brain is off")
        if (baseUrl.isBlank() || model.isBlank()) {
            return BrainState(BrainStatus.CLOUD_SETUP_REQUIRED, "Add a Base URL and Model")
        }
        if (!hasKey && needsApiKey(baseUrl)) {
            return BrainState(BrainStatus.CLOUD_SETUP_REQUIRED, "Cloud Brain needs API configuration")
        }
        if (lastError != null) return BrainState(BrainStatus.CLOUD_ERROR, lastError.take(140))
        return BrainState(BrainStatus.CLOUD_READY, "$model · ${hostOf(baseUrl)}")
    }

    fun needsApiKey(baseUrl: String): Boolean {
        val host = hostOf(baseUrl).lowercase()
        if (host.isEmpty()) return true
        if (host == "localhost" || host.endsWith(".localhost")) return false
        if (host == "127.0.0.1" || host == "::1" || host == "[::1]") return false
        if (host.startsWith("10.") || host.startsWith("192.168.")) return false
        if (host.startsWith("172.")) {
            val second = host.split(".").getOrNull(1)?.toIntOrNull()
            if (second != null && second in 16..31) return false
        }
        return true
    }

    fun hostOf(baseUrl: String): String {
        val viaUri = runCatching { java.net.URI(baseUrl).host.orEmpty() }.getOrDefault("")
        if (viaUri.isNotBlank()) return viaUri
        return baseUrl.substringAfter("://").substringBefore("/").ifBlank { "custom" }
    }
}
