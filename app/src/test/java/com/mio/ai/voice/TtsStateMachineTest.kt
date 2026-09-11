package com.mio.ai.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the TTS state machine contract: IDLE → SPEAKING →
 * FINISHED / ERROR → IDLE, with exactly-once settlement of every utterance.
 */
class TtsStateMachineTest {

    private class Probe {
        var done = 0
        var errors = ArrayList<String>()
        val onDone: () -> Unit = { done++ }
        val onError: (String) -> Unit = { errors += it }
    }

    @Test
    fun `normal flow finishes and returns to idle`() {
        val m = TtsStateMachine()
        val p = Probe()
        m.begin(listOf("a", "b"), p.onDone, p.onError)
        assertEquals(TtsState.SPEAKING, m.state)
        // Non-final chunk keeps us speaking.
        assertNull(m.onLastChunkDone("a"))
        assertEquals(TtsState.SPEAKING, m.state)
        m.onLastChunkDone("b")?.deliver()
        assertEquals(1, p.done)
        assertTrue(p.errors.isEmpty())
        assertEquals(TtsState.IDLE, m.state)
        assertEquals(
            listOf(
                TtsState.IDLE to TtsState.SPEAKING,
                TtsState.SPEAKING to TtsState.FINISHED,
                TtsState.FINISHED to TtsState.IDLE,
            ),
            m.transitions(),
        )
    }

    @Test
    fun `engine error reports once and returns to idle`() {
        val m = TtsStateMachine()
        val p = Probe()
        m.begin(listOf("a"), p.onDone, p.onError)
        m.onEngineError("a", "boom")?.deliver()
        assertEquals(listOf("boom"), p.errors)
        assertEquals(1, p.done) // onDone always runs, even on failure
        assertEquals(TtsState.IDLE, m.state)
        assertEquals(
            listOf(
                TtsState.IDLE to TtsState.SPEAKING,
                TtsState.SPEAKING to TtsState.ERROR,
                TtsState.ERROR to TtsState.IDLE,
            ),
            m.transitions(),
        )
    }

    @Test
    fun `new utterance supersedes the previous one and settles it`() {
        val m = TtsStateMachine()
        val first = Probe()
        val second = Probe()
        m.begin(listOf("a1"), first.onDone, first.onError)
        val again = m.begin(listOf("b1"), second.onDone, second.onError)
        assertNotNull(again.superseded)
        again.superseded?.deliver()
        assertEquals(1, first.done)
        assertTrue(first.errors.isEmpty()) // supersede is a cancel, not an error
        assertEquals(TtsState.SPEAKING, m.state)
        m.onLastChunkDone("b1")?.deliver()
        assertEquals(1, second.done)
        assertEquals(TtsState.IDLE, m.state)
    }

    @Test
    fun `stop cancels exactly once and late callbacks are ignored`() {
        val m = TtsStateMachine()
        val p = Probe()
        m.begin(listOf("a"), p.onDone, p.onError)
        m.cancel()?.deliver()
        assertEquals(1, p.done)
        assertTrue(p.errors.isEmpty())
        // Second cancel and late engine callbacks are all no-ops.
        assertNull(m.cancel())
        assertNull(m.onLastChunkDone("a"))
        assertNull(m.onEngineError("a", "late"))
        assertNull(m.onInterrupted("a"))
        assertEquals(1, p.done)
        assertEquals(TtsState.IDLE, m.state)
    }

    @Test
    fun `stale chunk ids from a superseded utterance are ignored`() {
        val m = TtsStateMachine()
        val p = Probe()
        m.begin(listOf("old"), p.onDone, p.onError)
        // The SECOND begin supersedes "old" — deliver that completion.
        m.begin(listOf("new"), p.onDone, p.onError).superseded?.deliver()
        assertNull(m.onLastChunkDone("old"))
        assertNull(m.onEngineError("old", "late"))
        assertEquals(TtsState.SPEAKING, m.state)
        m.onLastChunkDone("new")?.deliver()
        assertEquals(2, p.done) // superseded + finished
        assertEquals(TtsState.IDLE, m.state)
    }

    @Test
    fun `interruption settles the current utterance`() {
        val m = TtsStateMachine()
        val p = Probe()
        m.begin(listOf("a", "b"), p.onDone, p.onError)
        m.onInterrupted("a")?.deliver()
        assertEquals(1, p.done)
        assertTrue(p.errors.isEmpty())
        assertEquals(TtsState.IDLE, m.state)
    }

    @Test
    fun `duplicate engine callbacks are idempotent`() {
        val m = TtsStateMachine()
        val p = Probe()
        m.begin(listOf("a"), p.onDone, p.onError)
        m.onLastChunkDone("a")?.deliver()
        assertNull(m.onLastChunkDone("a"))
        assertNull(m.onEngineError("a", "late"))
        assertEquals(1, p.done)
        assertTrue(p.errors.isEmpty())
    }

    @Test
    fun `rapid command sequence never wedges and settles everything`() {
        // Command 1 → 2 → 3 back-to-back: each new response supersedes the last.
        val m = TtsStateMachine()
        var done = 0
        repeat(3) { i ->
            m.begin(listOf("c$i"), onDone = { done++ }, onError = {}).superseded?.deliver()
            assertEquals(TtsState.SPEAKING, m.state)
        }
        m.cancel()?.deliver()
        assertEquals(3, done)
        assertEquals(TtsState.IDLE, m.state)
    }

    @Test
    fun `failure without utterance is observable as error then idle`() {
        val m = TtsStateMachine()
        m.recordFailureWithoutUtterance()
        assertEquals(TtsState.IDLE, m.state)
        assertEquals(
            listOf(TtsState.IDLE to TtsState.ERROR, TtsState.ERROR to TtsState.IDLE),
            m.transitions(),
        )
    }
}
