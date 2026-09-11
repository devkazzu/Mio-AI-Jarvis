package com.mio.ai.core.ai

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

class CloudErrorsTest {

    @Test
    fun `http status mapping`() {
        assertTrue(CloudErrors.http(400, "", "m").contains("Bad request"))
        assertTrue(CloudErrors.http(401, "", "m").contains("Invalid API key"))
        assertTrue(CloudErrors.http(403, "", "m").contains("forbidden"))
        assertTrue(CloudErrors.http(404, "nope", "m").contains("Base URL"))
        assertTrue(CloudErrors.http(408, "", "m").contains("timed out"))
        assertTrue(CloudErrors.http(409, "", "m").contains("Conflict"))
        assertTrue(CloudErrors.http(429, "", "m").contains("Rate limit"))
        assertTrue(CloudErrors.http(500, "", "m").contains("Server error"))
        assertTrue(CloudErrors.http(503, "", "m").contains("Server error"))
        assertTrue(CloudErrors.http(418, "teapot", "m").contains("418"))
    }

    @Test
    fun `404 mentioning a model names the model`() {
        val msg = CloudErrors.http(404, """{"error":{"message":"model_not_found"}}""", "gpt-9")
        assertTrue(msg.contains("gpt-9"))
        assertTrue(msg.contains("unavailable"))
    }

    @Test
    fun `transport mapping`() {
        assertTrue(CloudErrors.transport(UnknownHostException()).contains("Network unavailable"))
        assertTrue(CloudErrors.transport(SocketTimeoutException()).contains("Timed out"))
        assertTrue(CloudErrors.transport(SSLException("x")).contains("Secure connection"))
        assertTrue(CloudErrors.transport(IllegalArgumentException()).contains("looks invalid"))
        assertTrue(CloudErrors.transport(ConnectException("refused")).contains("is it running"))
        assertTrue(CloudErrors.transport(IOException("boom")).contains("Couldn't reach"))
    }

    @Test
    fun `key-like patterns are scrubbed from transport errors`() {
        val msg = CloudErrors.transport(IOException("leaked sk-abc123XYZ_-oops here"))
        assertTrue(msg.contains("[redacted]"))
        assertFalse(msg.contains("sk-abc123XYZ_-oops"))
    }
}
