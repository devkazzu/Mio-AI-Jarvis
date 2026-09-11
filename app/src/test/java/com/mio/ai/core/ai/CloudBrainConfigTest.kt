package com.mio.ai.core.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudBrainConfigTest {

    @Test
    fun `overrides win over defaults`() {
        val e = CloudBrainConfig.effective(
            "https://proxy.example.com/v1", "my-model",
            "https://api.openai.com/v1", "gpt-4o-mini",
        )
        assertEquals("https://proxy.example.com/v1", e.baseUrl)
        assertEquals("my-model", e.model)
    }

    @Test
    fun `blanks fall back to defaults and trim`() {
        val e = CloudBrainConfig.effective(
            "  ", "",
            "https://api.openai.com/v1", "gpt-4o-mini",
        )
        assertEquals("https://api.openai.com/v1", e.baseUrl)
        assertEquals("gpt-4o-mini", e.model)
    }

    @Test
    fun `isReady matrix`() {
        val remote = EffectiveCloudConfig("https://api.openai.com/v1", "gpt-4o-mini")
        val local = EffectiveCloudConfig("http://127.0.0.1:11434/v1", "llama3.1")
        val blank = EffectiveCloudConfig("", "")
        assertFalse(CloudBrainConfig.isReady(false, remote, true))
        assertFalse(CloudBrainConfig.isReady(true, blank, true))
        assertFalse(CloudBrainConfig.isReady(true, remote, false))
        assertTrue(CloudBrainConfig.isReady(true, remote, true))
        assertTrue(CloudBrainConfig.isReady(true, local, false))
    }
}
