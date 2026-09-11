package com.mio.ai.voice

import android.speech.SpeechRecognizer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechErrorTextTest {

    @Test
    fun `busy error is actionable and non-fatal`() {
        val (message, fatal) = SpeechErrorText.messageFor(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertFalse(fatal)
        assertTrue(message.contains("busy", ignoreCase = true))
        assertTrue(message.contains("microphone", ignoreCase = true))
    }

    @Test
    fun `client error is a plain retryable interruption`() {
        val (message, fatal) = SpeechErrorText.messageFor(SpeechRecognizer.ERROR_CLIENT)
        assertFalse(fatal)
        assertTrue(message.contains("interrupted", ignoreCase = true))
    }

    @Test
    fun `permission denial is fatal`() {
        val (message, fatal) = SpeechErrorText.messageFor(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
        assertTrue(fatal)
        assertTrue(message.contains("permission", ignoreCase = true))
    }

    @Test
    fun `no-match and timeouts are retryable`() {
        assertFalse(SpeechErrorText.messageFor(SpeechRecognizer.ERROR_NO_MATCH).second)
        assertFalse(SpeechErrorText.messageFor(SpeechRecognizer.ERROR_SPEECH_TIMEOUT).second)
        assertFalse(SpeechErrorText.messageFor(SpeechRecognizer.ERROR_NETWORK).second)
        assertFalse(SpeechErrorText.messageFor(SpeechRecognizer.ERROR_SERVER).second)
    }

    @Test
    fun `unknown codes include the code`() {
        val (message, fatal) = SpeechErrorText.messageFor(999)
        assertFalse(fatal)
        assertTrue(message.contains("999"))
    }
}
