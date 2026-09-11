package com.mio.ai.core.ai

/**
 * Pure validation for the Settings → AI endpoint fields.
 *
 * Rules mirror [OpenAiCompatibleClient]'s expectations: blank Base URL means
 * "fully offline" (valid), otherwise it must be an http(s) URL with a host —
 * and must NOT already contain `/chat/completions`, which the client appends
 * itself. Blank Model means "saved/default model" (valid).
 *
 * Each function returns a user-facing error message, or null when valid.
 * Pure JVM code (no Android APIs) so it is unit-testable.
 */
object EndpointValidation {

    fun baseUrlError(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null // Empty = offline mode.
        if (v.any { it.isWhitespace() }) return "Base URL can't contain spaces."
        if (v.endsWith("/chat/completions", ignoreCase = true)) {
            return "End at /v1 — Mio adds /chat/completions itself."
        }
        val uri = try {
            java.net.URI(v)
        } catch (_: Exception) {
            return "That doesn't look like a valid URL."
        }
        val scheme = (uri.scheme ?: "").lowercase()
        if (scheme != "https" && scheme != "http") {
            return "Base URL must start with http:// or https://."
        }
        if (uri.host.isNullOrBlank()) {
            return "Base URL needs a host, e.g. https://api.openai.com/v1."
        }
        return null
    }

    fun modelError(raw: String): String? {
        val v = raw.trim()
        if (v.isEmpty()) return null // Empty = saved/default model.
        if (v.length > 80) return "Model name is too long."
        if (v.any { it.isWhitespace() }) return "Model names can't contain spaces."
        if (!v.all { it.isLetterOrDigit() || it in "._:/-+" }) {
            return "Model has invalid characters."
        }
        return null
    }
}
