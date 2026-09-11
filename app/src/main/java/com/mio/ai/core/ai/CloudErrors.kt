package com.mio.ai.core.ai

import com.mio.ai.core.util.MiniJson
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Every cloud failure becomes exactly one user-friendly sentence here — used
 * by both live requests ([OpenAiCompatibleClient.chat]) and the Settings
 * connection test, so users see the same language everywhere.
 *
 * Security invariant: these messages never carry credentials. The key lives
 * only in the `Authorization` header, but key-like patterns are scrubbed
 * anyway as defense in depth.
 */
internal object CloudErrors {

    fun http(code: Int, body: String, model: String): String = when (code) {
        400 -> "Bad request (400) — the server rejected the parameters."
        401 -> "Invalid API key (401) — check the key and try again."
        403 -> "Access forbidden (403) — this key can't use that endpoint."
        404 -> if (body.contains("model", ignoreCase = true) && model.isNotBlank()) {
            "Model “$model” unavailable (404) — check the model name."
        } else {
            "Not found (404) — is the Base URL right? It should end with /v1."
        }
        408 -> "Request timed out (408) — try again."
        409 -> "Conflict (409) — the server rejected the request state."
        429 -> "Rate limit reached (429) — wait a moment and retry."
        in 500..599 -> "Server error ($code) — try again in a bit."
        else -> "Request failed ($code): ${shortServerMessage(body)}"
    }

    fun transport(e: Exception): String = when (e) {
        is UnknownHostException ->
            "Network unavailable — host not found. Check the Base URL and your connection."
        is SocketTimeoutException ->
            "Timed out — the server didn't answer. Check the URL and your connection."
        is SSLException ->
            "Secure connection failed — use https:// and check the URL."
        is IllegalArgumentException ->
            "That Base URL looks invalid."
        is ConnectException ->
            "Couldn't reach the server — is it running?"
        is IOException ->
            "Couldn't reach the server (${safe(e.message)})."
        else ->
            "Request failed (${safe(e.message)})."
    }

    fun shortServerMessage(body: String): String {
        val root = MiniJson.asMap(runCatching { MiniJson.parse(body) }.getOrNull())
        val err = MiniJson.asMap(root["error"])
        return (MiniJson.asString(err["message"]) ?: body.ifBlank { "unknown error" }).take(160)
    }

    fun safe(message: String?): String = (message ?: "network error")
        .replace(Regex("sk-[A-Za-z0-9_.-]+"), "[redacted]")
        .take(120)
}
