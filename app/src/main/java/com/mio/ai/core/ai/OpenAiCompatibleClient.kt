package com.mio.ai.core.ai

import com.mio.ai.core.util.MiniJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to any OpenAI-compatible `/chat/completions` endpoint.
 *
 * Keys/endpoints come from BuildConfig (local.properties) or the encrypted
 * in-app override — never from source. An empty API key is allowed for local
 * servers (Ollama/LM Studio) that don't need auth.
 */
class OpenAiCompatibleClient(
    baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val http: OkHttpClient = defaultHttp(),
) : AiClient {

    private val endpoint = baseUrl.trim().trimEnd('/') + "/chat/completions"

    override val isConfigured: Boolean
        get() = endpoint.isNotBlank() && model.isNotBlank() && endpoint.startsWith("http")

    override val describe: String
        get() = "$model · ${endpoint.substringAfter("://").substringBefore("/").ifBlank { "custom" }}"

    override suspend fun chat(messages: List<ChatMessage>, systemPrompt: String): String =
        withContext(Dispatchers.IO) {
            if (!isConfigured) throw AiException("Cloud brain is not configured.")
            val payload = mapOf(
                "model" to model,
                "temperature" to 0.4,
                "max_tokens" to 600,
                "messages" to buildList {
                    if (systemPrompt.isNotBlank()) {
                        add(mapOf("role" to "system", "content" to systemPrompt))
                    }
                    messages.forEach { add(mapOf("role" to it.role.name.lowercase(), "content" to it.content)) }
                },
            )
            val reqBuilder = Request.Builder()
                .url(endpoint)
                .post(MiniJson.stringify(payload).toRequestBody("application/json".toMediaType()))
            if (apiKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer $apiKey")
            try {
                http.newCall(reqBuilder.build()).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        throw AiException("AI service error ${resp.code}: ${shortError(body)}")
                    }
                    extractContent(body) ?: throw AiException("AI returned an empty reply.")
                }
            } catch (e: AiException) {
                throw e
            } catch (e: Exception) {
                throw AiException("Could not reach the AI service (${e.message ?: "network error"}).", e)
            }
        }

    private fun extractContent(body: String): String? {
        val root = MiniJson.asMap(MiniJson.parse(body))
        val choices = MiniJson.asList(root["choices"])
        val first = MiniJson.asMap(choices.firstOrNull())
        val message = MiniJson.asMap(first["message"])
        return MiniJson.asString(message["content"])?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun shortError(body: String): String {
        val root = MiniJson.asMap(MiniJson.parse(body))
        val err = MiniJson.asMap(root["error"])
        return (MiniJson.asString(err["message"]) ?: body).take(160)
    }

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
