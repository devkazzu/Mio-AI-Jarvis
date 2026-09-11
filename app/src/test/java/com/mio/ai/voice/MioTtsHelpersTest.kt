package com.mio.ai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the real TTS text helpers (top-level internals in MioTts.kt). */
class MioTtsHelpersTest {

    @Test
    fun `sanitize strips markdown and collapses whitespace`() {
        assertEquals("Hello, world", sanitize("**Hello** ·  world"))
        assertEquals("a b c", sanitize("a `b` #c"))
    }

    @Test
    fun `sanitize caps length`() {
        assertEquals(1500, sanitize("x".repeat(2000)).length)
    }

    @Test
    fun `splitSentences keeps delimiters with sentences`() {
        assertEquals(
            listOf("Hello world.", "How are you?", "Fine, thanks!"),
            splitSentences("Hello world. How are you? Fine, thanks!"),
        )
    }

    @Test
    fun `chunk keeps short text whole and splits long text`() {
        assertEquals(listOf("short"), chunk("short"))
        val long = "Sentence one is here. Sentence two is here. Sentence three is here. " +
            "Sentence four is here. Sentence five is here. Sentence six is here. " +
            "Sentence seven is here. Sentence eight is here."
        val chunks = chunk(long, maxLen = 60)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 80 })
        assertEquals(long.split(" ").size, chunks.joinToString(" ").split(" ").size)
    }
}
