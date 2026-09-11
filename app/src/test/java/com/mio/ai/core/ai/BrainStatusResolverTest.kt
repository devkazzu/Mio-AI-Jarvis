package com.mio.ai.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainStatusResolverTest {

    private fun resolve(
        useCloud: Boolean = true,
        baseUrl: String = "https://api.openai.com/v1",
        model: String = "gpt-4o-mini",
        hasKey: Boolean = true,
        lastError: String? = null,
    ) = BrainStatusResolver.resolve(useCloud, baseUrl, model, hasKey, lastError)

    @Test
    fun `toggle off is always offline`() {
        val s = resolve(useCloud = false)
        assertEquals(BrainStatus.OFFLINE, s.status)
        assertEquals("OFFLINE BRAIN", s.label)
    }

    @Test
    fun `complete config is ready with model and host detail`() {
        val s = resolve()
        assertEquals(BrainStatus.CLOUD_READY, s.status)
        assertEquals("CLOUD BRAIN", s.label)
        assertEquals("gpt-4o-mini · api.openai.com", s.detail)
    }

    @Test
    fun `missing url or model needs setup`() {
        assertEquals(BrainStatus.CLOUD_SETUP_REQUIRED, resolve(baseUrl = "").status)
        assertEquals(BrainStatus.CLOUD_SETUP_REQUIRED, resolve(model = "").status)
    }

    @Test
    fun `missing key on remote host needs setup`() {
        val s = resolve(hasKey = false)
        assertEquals(BrainStatus.CLOUD_SETUP_REQUIRED, s.status)
        assertEquals("CLOUD SETUP REQUIRED", s.label)
        assertEquals("Cloud Brain needs API configuration", s.detail)
    }

    @Test
    fun `local hosts work without a key`() {
        assertEquals(BrainStatus.CLOUD_READY, resolve(baseUrl = "http://localhost:11434/v1", hasKey = false).status)
        assertEquals(BrainStatus.CLOUD_READY, resolve(baseUrl = "http://127.0.0.1:11434/v1", hasKey = false).status)
        assertEquals(BrainStatus.CLOUD_READY, resolve(baseUrl = "http://192.168.1.5:11434/v1", hasKey = false).status)
        assertEquals(BrainStatus.CLOUD_READY, resolve(baseUrl = "http://10.0.2.2:11434/v1", hasKey = false).status)
    }

    @Test
    fun `last error surfaces as cloud error`() {
        val s = resolve(lastError = "Invalid API key (401) — check the key and try again.")
        assertEquals(BrainStatus.CLOUD_ERROR, s.status)
        assertEquals("CLOUD BRAIN ERROR", s.label)
        assertTrue(s.detail.contains("Invalid API key"))
    }

    @Test
    fun `needsApiKey host matrix`() {
        assertTrue(BrainStatusResolver.needsApiKey("https://api.openai.com/v1"))
        assertTrue(BrainStatusResolver.needsApiKey("https://my-proxy.example.com/v1"))
        assertTrue(BrainStatusResolver.needsApiKey("http://8.8.8.8:11434/v1"))
        assertTrue(BrainStatusResolver.needsApiKey("http://172.32.0.1:11434/v1"))
        assertTrue(BrainStatusResolver.needsApiKey(""))
        assertFalse(BrainStatusResolver.needsApiKey("http://localhost:11434/v1"))
        assertFalse(BrainStatusResolver.needsApiKey("http://localhost/v1"))
        assertFalse(BrainStatusResolver.needsApiKey("http://127.0.0.1:8080/v1"))
        assertFalse(BrainStatusResolver.needsApiKey("http://10.1.2.3/v1"))
        assertFalse(BrainStatusResolver.needsApiKey("http://192.168.0.10/v1"))
        assertFalse(BrainStatusResolver.needsApiKey("http://172.16.5.4/v1"))
        assertFalse(BrainStatusResolver.needsApiKey("http://172.31.255.1/v1"))
    }
}
