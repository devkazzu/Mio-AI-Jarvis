package com.mio.ai.core.ai

import android.os.SystemClock
import com.mio.ai.core.util.MiniJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Talks to any OpenAI-compatible `/chat/completions` endpoint.
 *
 * Configuration comes from Settings (overrides) plus build defaults for the
 * Base URL/model; the API key comes ONLY from encrypted in-app storage (set
 * in Settings → AI) — never from source, resources, or build config. An
 * empty key is allowed for local servers (Ollama/LM Studio) that don't need
 * auth.
 *
 * Reliability: one bounded retry for transient failures (timeouts, 408/429,
 * 5xx) — never for auth errors (401/403/404). Everything runs on
 * Dispatchers.IO; the main thread is never blocked.
 *
 * Security invariant: the API key travels ONLY in the `Authorization`
 * header. It is never logged, never stored here, and never included in any
 * error message — see [CloudErrors].
 */
class OpenAiCompatibleClient(
    baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val http: OkHttpClient = defaultHttp(),
) : AiClient {

    private val root = baseUrl.trim().trimEnd('/')
    private val endpoint = "$root/chat/completions"

    override val isConfigured: Boolean
        get() = endpoint.isNotBlank() && model.isNotBlank() && endpoint.startsWith("http")

    override val describe: String
        get() = "$model · ${endpoint.substringAfter("://").substringBefore("/").ifBlank { "custom" }}"

    override suspend fun chat(
        messages: List<ChatMessage>,
        systemPrompt: String,
        maxTokens: Int,
    ): String =
        withContext(Dispatchers.IO) {
            if (!isConfigured) throw AiException("Cloud brain is not configured.")
            val payload = mapOf(
                "model" to model,
                "temperature" to 0.4,
                "max_tokens" to maxTokens.coerceIn(60, 2000),
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
            val request = reqBuilder.build()

            var lastTransient: AiException? = null
            repeat(MAX_ATTEMPTS) { attempt ->
                val lastAttempt = attempt + 1 >= MAX_ATTEMPTS
                try {
                    http.newCall(request).execute().use { resp ->
                        val body = resp.body?.string().orEmpty()
                        if (!resp.isSuccessful) {
                            val mapped = AiException(CloudErrors.http(resp.code, body, model))
                            // Transient statuses earn one delayed retry; auth and
                            // validation failures propagate immediately, never retried.
                            if (resp.code in RETRY_STATUS && !lastAttempt) lastTransient = mapped
                            throw mapped
                        }
                        return@withContext extractContent(body)
                            ?: throw AiException("AI returned an empty reply.")
                    }
                } catch (e: AiException) {
                    if (e === lastTransient && !lastAttempt) {
                        delay(RETRY_DELAY_MS)
                    } else {
                        throw e
                    }
                } catch (e: SocketTimeoutException) {
                    val mapped = AiException(CloudErrors.transport(e), e)
                    if (!lastAttempt) {
                        lastTransient = mapped
                        delay(RETRY_DELAY_MS)
                    } else {
                        throw mapped
                    }
                } catch (e: Exception) {
                    throw AiException(CloudErrors.transport(e), e)
                }
            }
            throw lastTransient ?: AiException("AI request failed.")
        }

    override suspend fun ping(): PingResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext PingResult.Fail("Add a Base URL and Model first.")
        // Short, bounded timeouts and NO retry: a connection test must fail fast.
        val probe = http.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        val started = SystemClock.elapsedRealtime()
        try {
            val reqBuilder = Request.Builder().url("$root/models").get()
            if (apiKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer $apiKey")
            probe.newCall(reqBuilder.build()).execute().use { resp ->
                val ms = SystemClock.elapsedRealtime() - started
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext PingResult.Fail(CloudErrors.http(resp.code, body, model))
                val note = modelNote(body)
                PingResult.Ok("Connected in ${ms} ms" + (note?.let { " · $it" } ?: ""))
            }
        } catch (e: Exception) {
            PingResult.Fail(CloudErrors.transport(e))
        }
    }

    /**
     * Pull the assistant text out of a chat-completions response. Malformed
     * bodies fail closed to null (→ "empty reply"), never to an exception
     * that would look like a network failure.
     */
    private fun extractContent(body: String): String? {
        val root = MiniJson.asMap(runCatching { MiniJson.parse(body) }.getOrNull())
        val choices = MiniJson.asList(root["choices"])
        val first = MiniJson.asMap(choices.firstOrNull())
        val message = MiniJson.asMap(first["message"])
        return MiniJson.asString(message["content"])?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Warn when the configured model isn't advertised by the server.
     * Servers that omit the model list stay quiet (no nagging).
     */
    private fun modelNote(body: String): String? {
        if (model.isBlank()) return null
        val ids = MiniJson.asList(MiniJson.asMap(runCatching { MiniJson.parse(body) }.getOrNull())["data"])
            .mapNotNull { MiniJson.asString(MiniJson.asMap(it)["id"]) }
        if (ids.isEmpty()) return null
        return if (ids.none { it.equals(model, ignoreCase = true) }) {
            "model “$model” isn't listed by this server"
        } else {
            null
        }
    }

    companion object {
        /** One initial attempt plus one retry — bounded, never infinite. */
        private const val MAX_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 1_200L
        private val RETRY_STATUS = setOf(408, 429, 500, 502, 503, 504)

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(75, TimeUnit.SECONDS)
            .build()
    }
}
