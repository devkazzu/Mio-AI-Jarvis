package com.mio.ai.core.ai

/**
 * Minimal chat interface for the cloud brain. Any OpenAI-compatible endpoint
 * (OpenAI, Azure OpenAI, Ollama, LM Studio, OpenRouter, Groq, Together…)
 * can back it — see [OpenAiCompatibleClient].
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
    suspend fun chat(messages: List<ChatMessage>, systemPrompt: String): String
}

data class ChatMessage(val role: Role, val content: String) {
    enum class Role { SYSTEM, USER, ASSISTANT }
}

class AiException(message: String, cause: Throwable? = null) : Exception(message, cause)
