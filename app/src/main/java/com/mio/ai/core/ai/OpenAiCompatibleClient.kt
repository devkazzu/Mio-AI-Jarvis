package com.mio.ai.core.ai

import android.os.SystemClock
import com.mio.ai.core.util.MiniJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Talks to any OpenAI-compatible `/chat/completions` endpoint.
 *
 * Keys/endpoints come from BuildConfig (local.properties) or the encrypted
 * in-app override — never from source. An empty API key is allowed for local
 * servers (Ollama/LM Studio) that don't need auth.
 *
 * Security invariant: the API key travels ONLY in the `Authorization`
 * header. It is never logged, never stored here, and never included in any
 * error message — see [ping]/[chat] handling below.
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
                throw AiException("Could not reach the AI service (${safe(e.message)}).", e)
            }
        }

    override suspend fun ping(): PingResult = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext PingResult.Fail("Add a Base URL and Model first.")
        // Short, bounded timeouts: a connection test must fail fast.
        val probe = http.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
        val reqBuilder = Request.Builder().url("$root/models").get()
        if (apiKey.isNotBlank()) reqBuilder.header("Authorization", "Bearer $apiKey")
        val started = SystemClock.elapsedRealtime()
        try {
            probe.newCall(reqBuilder.build()).execute().use { resp ->
                val ms = SystemClock.elapsedRealtime() - started
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) return@withContext PingResult.Fail(httpError(resp.code, body))
                val note = modelNote(body)
                PingResult.Ok("Connected in ${ms} ms" + (note?.let { " · $it" } ?: ""))
            }
        } catch (e: UnknownHostException) {
            PingResult.Fail("Host not found — check the Base URL.")
        } catch (e: SocketTimeoutException) {
            PingResult.Fail("Timed out — the server didn't answer. Check the URL and your connection.")
        } catch (e: SSLException) {
            PingResult.Fail("Secure connection failed — use https:// and check the URL.")
        } catch (e: IllegalArgumentException) {
            PingResult.Fail("That Base URL looks invalid.")
        } catch (e: IOException) {
            PingResult.Fail("Couldn't reach the server (${safe(e.message)}).")
        } catch (e: Exception) {
            PingResult.Fail("Test failed (${safe(e.message)}).")
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

    /** Specific, actionable server-error messages (never include credentials). */
    private fun httpError(code: Int, body: String): String = when (code) {
        401 -> "Invalid API key (401) — check the key and try again."
        403 -> "Access forbidden (403) — this key can't use that endpoint."
        404 -> "Not found (404) — is the Base URL right? It should end with /v1."
        429 -> "Rate limited (429) — wait a moment and retry."
        in 500..599 -> "Server error ($code) — try again in a bit."
        else -> "Request failed ($code): ${shortError(body)}"
    }

    /**
     * Warn when the configured model isn't advertised by the server.
     * Servers that omit the model list stay quiet (no nagging).
     */
    private fun modelNote(body: String): String? {
        if (model.isBlank()) return null
        val ids = MiniJson.asList(MiniJson.asMap(MiniJson.parse(body))["data"])
            .mapNotNull { MiniJson.asString(MiniJson.asMap(it)["id"]) }
        if (ids.isEmpty()) return null
        return if (ids.none { it.equals(model, ignoreCase = true) }) {
            "model “$model” isn't listed by this server"
        } else {
            null
        }
    }

    /**
     * Transport errors only ever mention the host — the key lives solely in
     * the request header — but scrub key-like patterns anyway as defense in
     * depth, and keep messages short for speech/UI.
     */
    private fun safe(message: String?): String = (message ?: "network error")
        .replace(Regex("sk-[A-Za-z0-9_.-]+"), "[redacted]")
        .take(120)

    companion object {
        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
