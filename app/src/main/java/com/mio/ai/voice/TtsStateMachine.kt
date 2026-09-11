package com.mio.ai.voice

/**
 * Observable TTS engine state.
 *
 * Lifecycle: IDLE → SPEAKING → FINISHED / ERROR → IDLE.
 * FINISHED and ERROR are transient terminal states — the machine always
 * settles back to IDLE in the same step, so observers can never wedge.
 */
enum class TtsState { IDLE, SPEAKING, FINISHED, ERROR }

/**
 * Pure, thread-safe utterance state machine for [MioTts].
 *
 * Guarantees (all unit-tested in TtsStateMachineTest):
 * - Every begun utterance settles **exactly once** (finished / failed /
 *   cancelled) — no lost completions, no double deliveries.
 * - A new utterance safely supersedes a speaking one; the old utterance is
 *   settled as cancelled and its audio is stopped by the owner.
 * - Stale engine callbacks (wrong utterance id, late duplicates, callbacks
 *   after stop/shutdown) are ignored instead of corrupting state.
 * - The machine always returns to [TtsState.IDLE].
 *
 * Threading: all methods are synchronized. [Completion.deliver] must be
 * called OUTSIDE the machine (never hold the monitor while invoking
 * callbacks — they may re-enter the machine via stop()/speak()).
 *
 * Pure JVM code (no Android APIs) so the whole contract is unit-testable.
 */
class TtsStateMachine {

    /** An utterance that reached a terminal state and needs its callbacks run. */
    data class Completion(
        val utteranceId: Long,
        /** Null for finished/cancelled; user-facing reason when the engine failed. */
        val errorMessage: String?,
        val onDone: (() -> Unit)?,
        val onError: ((String) -> Unit)?,
    ) {
        /**
         * Run callbacks: [onError] first when failed, then ALWAYS [onDone].
         * Call only from outside [TtsStateMachine] synchronization.
         */
        fun deliver() {
            if (errorMessage != null) onError?.invoke(errorMessage)
            onDone?.invoke()
        }
    }

    data class BeginResult(
        val utteranceId: Long,
        /** Previous utterance to settle as cancelled (null when idle). */
        val superseded: Completion?,
    )

    private data class Active(
        val id: Long,
        val chunkIds: Set<String>,
        val lastChunkId: String,
        val onDone: (() -> Unit)?,
        val onError: ((String) -> Unit)?,
        var settled: Boolean = false,
    )

    @Volatile
    var state: TtsState = TtsState.IDLE
        private set

    private var current: Active? = null
    private var nextId = 1L
    private val transitionLog = ArrayDeque<Pair<TtsState, TtsState>>()

    /** Recent transitions (oldest first, bounded) for tests and debugging. */
    @Synchronized
    fun transitions(): List<Pair<TtsState, TtsState>> = transitionLog.toList()

    private fun moveTo(next: TtsState) {
        val prev = state
        if (prev == next) return
        state = next
        transitionLog.addLast(prev to next)
        while (transitionLog.size > 64) transitionLog.removeFirst()
    }

    /**
     * Begin a new utterance. When another utterance is active it is marked
     * settled and returned as [BeginResult.superseded] — the caller must stop
     * the audio and [Completion.deliver] it.
     */
    @Synchronized
    fun begin(
        chunkIds: List<String>,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?,
    ): BeginResult {
        require(chunkIds.isNotEmpty()) { "utterance needs at least one chunk" }
        val prev = current?.takeUnless { it.settled }
        prev?.settled = true
        val id = nextId++
        current = Active(id, chunkIds.toSet(), chunkIds.last(), onDone, onError)
        moveTo(TtsState.SPEAKING)
        return BeginResult(id, prev?.let { Completion(it.id, null, it.onDone, null) })
    }

    /**
     * Final-chunk completion from the engine. Non-final chunks and unknown
     * ids return null (stay SPEAKING); duplicates after settling return null.
     */
    @Synchronized
    fun onLastChunkDone(chunkId: String): Completion? {
        val c = current?.takeUnless { it.settled } ?: return null
        if (c.lastChunkId != chunkId) return null
        c.settled = true
        current = null
        moveTo(TtsState.FINISHED)
        moveTo(TtsState.IDLE)
        return Completion(c.id, null, c.onDone, null)
    }

    /** Engine error for one of the active utterance's chunks. */
    @Synchronized
    fun onEngineError(chunkId: String, message: String): Completion? {
        val c = current?.takeUnless { it.settled } ?: return null
        if (chunkId !in c.chunkIds) return null
        c.settled = true
        current = null
        moveTo(TtsState.ERROR)
        moveTo(TtsState.IDLE)
        return Completion(c.id, message, c.onDone, c.onError)
    }

    /**
     * Engine interruption (our stop/flush, or the system stealing audio).
     * A null id settles whatever is active; unknown ids are ignored so a
     * superseding utterance is never killed by the old one's teardown.
     */
    @Synchronized
    fun onInterrupted(chunkId: String?): Completion? {
        val c = current?.takeUnless { it.settled } ?: return null
        if (chunkId != null && chunkId !in c.chunkIds) return null
        c.settled = true
        current = null
        moveTo(TtsState.IDLE)
        return Completion(c.id, null, c.onDone, null)
    }

    /** Explicit stop: settle the active utterance as cancelled (no error). */
    @Synchronized
    fun cancel(): Completion? {
        val c = current?.takeUnless { it.settled } ?: return null
        c.settled = true
        current = null
        moveTo(TtsState.IDLE)
        return Completion(c.id, null, c.onDone, null)
    }

    /** Failure with no utterance (init failure): IDLE → ERROR → IDLE. */
    @Synchronized
    fun recordFailureWithoutUtterance() {
        moveTo(TtsState.ERROR)
        moveTo(TtsState.IDLE)
    }
}
