package com.mio.ai.core.ai

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointValidationTest {

    @Test
    fun `blank base url means offline and is valid`() {
        assertNull(EndpointValidation.baseUrlError(""))
        assertNull(EndpointValidation.baseUrlError("   "))
    }

    @Test
    fun `valid base urls pass`() {
        assertNull(EndpointValidation.baseUrlError("https://api.openai.com/v1"))
        assertNull(EndpointValidation.baseUrlError("http://localhost:11434/v1"))
        assertNull(EndpointValidation.baseUrlError("https://my-host.example:8080/openai"))
        assertNull(EndpointValidation.baseUrlError("  https://api.openai.com/v1  "))
    }

    @Test
    fun `bad schemes are rejected`() {
        assertNotNull(EndpointValidation.baseUrlError("ftp://files.example/v1"))
        assertNotNull(EndpointValidation.baseUrlError("api.openai.com/v1"))
        assertNotNull(EndpointValidation.baseUrlError("://missing"))
    }

    @Test
    fun `base url with spaces is rejected`() {
        assertNotNull(EndpointValidation.baseUrlError("https://api.openai.com/v 1"))
    }

    @Test
    fun `chat completions suffix is rejected with a hint`() {
        val err = EndpointValidation.baseUrlError("https://api.openai.com/v1/chat/completions")
        assertNotNull(err)
        assertTrue(err!!.contains("/v1"))
    }

    @Test
    fun `missing host is rejected`() {
        assertNotNull(EndpointValidation.baseUrlError("https://"))
    }

    @Test
    fun `blank model uses default and is valid`() {
        assertNull(EndpointValidation.modelError(""))
        assertNull(EndpointValidation.modelError("  "))
    }

    @Test
    fun `valid models pass`() {
        assertNull(EndpointValidation.modelError("gpt-4o-mini"))
        assertNull(EndpointValidation.modelError("llama3.1:8b"))
        assertNull(EndpointValidation.modelError("org/model-name_v2"))
    }

    @Test
    fun `model with spaces is rejected`() {
        assertNotNull(EndpointValidation.modelError("my model"))
    }

    @Test
    fun `model with bad characters is rejected`() {
        assertNotNull(EndpointValidation.modelError("model!"))
        assertNotNull(EndpointValidation.modelError("a;b"))
    }

    @Test
    fun `overlong model is rejected`() {
        assertNotNull(EndpointValidation.modelError("x".repeat(81)))
    }
}
