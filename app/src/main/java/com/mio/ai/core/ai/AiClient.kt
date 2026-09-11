package com.mio.ai.core.ai

/**
 * Minimal chat interface for the cloud brain. Any OpenAI-compatible endpoint
 * (OpenAI, Azure OpenAI, Ollama, LM Studio, OpenRouter, Groq, Together…)
 * can back it — see [OpenAiCompatibleClient].
 *
 * Architecture boundary: this interface is the ONLY thing the assistant
 * knows about AI. A future first-party backend/proxy (keyless app, auth at
 * the proxy) slots in as another implementation — no router, planner, or UI
 * changes required.
 */
interface AiClient {
    /** False when no endpoint is configured (offline mode). */
    val isConfigured: Boolean

    /** Human label for the Settings screen, e.g. "gpt-4o-mini · api.openai.com". */
    val describe: String

    /**
     * One chat completion. Throws [AiException] on transport/API failure so
     * callers can fall back to the offline brain.
     */
    suspend fun chat(
        messages: List<ChatMessage>,
        systemPrompt: String,
        maxTokens: Int = 450,
    ): String

    /**
     * Lightweight reachability check (`GET /models`) for the Settings
     * “Test connection” button. Never throws — failures become
     * [PingResult.Fail] with a user-facing message.
     */
    suspend fun ping(): PingResult
}

/** Result of [AiClient.ping]. [detail] is user-facing and never carries credentials. */
sealed interface PingResult {
    data class Ok(val detail: String) : PingResult
    data class Fail(val detail: String) : PingResult
}

data class ChatMessage(val role: Role, val content: String) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)
