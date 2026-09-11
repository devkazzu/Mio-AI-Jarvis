package com.mio.ai.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.mio.ai.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.UUID

/**
 * Text-to-speech with an explicit state machine ([TtsStateMachine]):
 * IDLE → SPEAKING → FINISHED / ERROR → IDLE.
 *
 * Hard guarantees:
 * - Every speak() settles exactly once ([onDone] always runs; [onError] too
 *   when the engine failed) — callers can never wedge waiting for speech.
 * - A new response safely interrupts the previous utterance (stop +
 *   settle-as-cancelled) instead of queueing behind it or erroring "busy".
 * - Init failure, engine errors, stop(), shutdown(), and lifecycle
 *   interruptions all return the machine to IDLE.
 * - speak() is a suspend function and never blocks the calling thread while
 *   waiting for init or completion.
 *
 * Privacy: transition logs carry utterance ids, chunk counts, and engine
 * error codes only — never the spoken text (it may contain user data).
 */
class MioTts(private val context: Context) {

    private val machine = TtsStateMachine()
    private val _state = MutableStateFlow(TtsState.IDLE)
    val state: StateFlow<TtsState> = _state.asStateFlow()

    /** Serializes utterance initiation (short critical section — never held across speech). */
    private val speakMutex = Mutex()
    private var tts: TextToSpeech? = null
    private var initStarted = false
    private var ready: CompletableDeferred<Boolean>? = null
    private val initCallbacks = ArrayList<(() -> Unit)>()

    @Volatile
    private var rate = 1.0f

    @Volatile
    private var pitch = 1.0f

    @Volatile
    private var preferredVoiceName: String? = null

    /**
     * Initialize the engine exactly once. Idempotent and safe to call from
     * any thread with a Looper (init callbacks are also re-run after
     * [shutdown]). [onDone] runs once the engine outcome is known.
     */
    @Synchronized
    fun init(onDone: (() -> Unit)? = null) {
        if (onDone != null) {
            if (tts != null && ready?.isCompleted == true) {
                onDone.invoke()
            } else {
                initCallbacks += onDone
            }
        }
        if (tts != null || initStarted) return
        initStarted = true
        ready = CompletableDeferred()
        debug("init: creating engine")
        tts = TextToSpeech(context.applicationContext, ::onEngineInit)
    }

    private fun onEngineInit(status: Int) {
        val callbacks: List<() -> Unit>
        synchronized(this) {
            callbacks = initCallbacks.toList()
            initCallbacks.clear()
            if (status == TextToSpeech.SUCCESS) {
                configure()
                _state.value = TtsState.IDLE
                ready?.complete(true)
                debug("init: engine ready")
            } else {
                Log.w(TAG, "init failed (status=$status)")
                machine.recordFailureWithoutUtterance()
                _state.value = machine.state
                tts = null
                initStarted = false
                ready?.complete(false)
            }
        }
        // Outside the lock: callbacks may re-enter (refreshVoices → engine).
        callbacks.forEach { runCatching { it.invoke() } }
    }

    fun setRatePitch(rate: Float, pitch: Float) {
        this.rate = rate.coerceIn(0.5f, 2.0f)
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        val tts = synchronized(this) { tts }
        tts?.setSpeechRate(this.rate)
        tts?.setPitch(this.pitch)
    }

    /**
     * Use a specific engine voice by [TextToSpeech.getVoices] name.
     * Stored even before init so the preference survives engine startup.
     */
    fun setVoiceByName(name: String): Boolean {
        preferredVoiceName = name.ifBlank { null }
        val tts = synchronized(this) { tts } ?: return name.isBlank()
        if (name.isBlank()) {
            runCatching { tts.voices }.getOrNull()?.let { pickBestVoice(it)?.let { v -> tts.voice = v } }
            return true
        }
        val voice = runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == name }
            ?: return false
        return runCatching { tts.setVoice(voice) }.getOrDefault(TextToSpeech.ERROR) ==
            TextToSpeech.SUCCESS
    }

    /** English voice names for the Settings picker (empty until engine ready). */
    fun englishVoiceNames(): List<String> {
        val tts = synchronized(this) { tts }
        return runCatching { tts?.voices }.getOrNull()
            ?.filter { it.locale.language == "en" }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()
    }

    /**
     * Speak [text], interrupting any active utterance first so the newest
     * response always wins — never a "busy" error.
     *
     * Suspends (without blocking) for init and for utterance initiation only;
     * completion arrives via [onDone]/[onError], each invoked exactly once.
     */
    suspend fun speak(
        text: String,
        enabled: Boolean,
        flush: Boolean = true,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null,
    ) {
        val clean = sanitize(text)
        if (clean.isBlank() || !enabled) {
            onDone?.invoke()
            return
        }
        if (!awaitReady(INIT_TIMEOUT_MS)) {
            failRequest(
                "Speech unavailable — the voice engine didn't start.",
                onDone, onError,
            ).forEach { it.deliver() }
            return
        }
        val chunks = chunk(clean)
        val ids = chunks.map { UUID.randomUUID().toString() }
        debug("speak: ${chunks.size} chunk(s), ${clean.length} chars")
        val completions = ArrayList<TtsStateMachine.Completion>(2)
        speakMutex.withLock {
            val eng = synchronized(this) { tts }
            if (eng == null) {
                completions += failRequest(
                    "Speech unavailable — the voice engine shut down.",
                    onDone, onError,
                )
                return@withLock
            }
            // Always halt superseded audio (even for queue-adds) so ghost speech can't linger.
            if (flush || machine.state == TtsState.SPEAKING) runCatching { eng.stop() }
            val begin = machine.begin(ids, onDone, onError)
            _state.value = machine.state
            debug("begin utterance ${begin.utteranceId}" + if (begin.superseded != null) " (superseded ${begin.superseded.utteranceId})" else "")
            begin.superseded?.let { completions += it }
            chunks.forEachIndexed { index, part ->
                if (machine.state != TtsState.SPEAKING) return@forEachIndexed
                val mode = if (index == 0 && flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
                val rc = runCatching { eng.speak(part, mode, Bundle(), ids[index]) }
                    .getOrDefault(TextToSpeech.ERROR)
                if (rc == TextToSpeech.ERROR) {
                    machine.onEngineError(ids[index], "Speech error — the engine rejected the utterance.")
                        ?.let { completions += it }
                    _state.value = machine.state
                }
            }
        }
        // Outside the mutex: callbacks may re-enter via stop()/speak().
        completions.forEach { it.deliver() }
    }

    /**
     * Stop any active speech. Safe from any thread. The pending utterance
     * (if any) settles as cancelled and the machine returns to IDLE.
     */
    fun stop() {
        val c = machine.cancel()
        _state.value = machine.state
        val eng = synchronized(this) { tts }
        runCatching { eng?.stop() }
        if (c != null) debug("stop: settled utterance ${c.utteranceId} as cancelled")
        c?.deliver()
    }

    /**
     * Release the engine (Activity/VM teardown). Settles any active utterance
     * and returns to IDLE; a later [init]/[speak] cleanly re-initializes.
     */
    fun shutdown() {
        val c = machine.cancel()
        val eng = synchronized(this) {
            val e = tts
            tts = null
            initStarted = false
            ready = null
            initCallbacks.clear()
            e
        }
        runCatching { eng?.stop() }
        runCatching { eng?.shutdown() }
        _state.value = TtsState.IDLE
        debug("shutdown")
        c?.deliver()
    }

    // ----------------------------------------------------------------- helpers

    /** Settle any stale utterance, record the failure, and build this request's error. */
    private fun failRequest(
        msg: String,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?,
    ): List<TtsStateMachine.Completion> {
        val out = ArrayList<TtsStateMachine.Completion>(2)
        machine.cancel()?.let { out += it }
        machine.recordFailureWithoutUtterance()
        _state.value = machine.state
        Log.w(TAG, "speak failed: $msg")
        out += TtsStateMachine.Completion(-1, msg, onDone, onError)
        return out
    }

    private suspend fun awaitReady(timeoutMs: Long): Boolean {
        init()
        val d = synchronized(this) { ready } ?: return false
        return withTimeoutOrNull(timeoutMs) { d.await() } == true
    }

    private fun configure() {
        val tts = this.tts ?: return
        val us = Locale.US
        val result = tts.setLanguage(us)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.language = Locale.UK
        }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        val preferred = preferredVoiceName?.takeIf { it.isNotBlank() }
        val voice = preferred?.let { name -> voices.firstOrNull { it.name == name } }
            ?: pickBestVoice(voices)
        voice?.let { runCatching { tts.voice = it } }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                debug("onStart id=${utteranceId?.take(8)}")
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == null) return
                val c = machine.onLastChunkDone(utteranceId)
                _state.value = machine.state
                if (c != null) debug("onDone id=${utteranceId.take(8)} → FINISHED")
                c?.deliver()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onEngineError(utteranceId, "synthesis error")
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                onEngineError(utteranceId, codeName(errorCode))
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                val c = machine.onInterrupted(utteranceId)
                _state.value = machine.state
                if (c != null) debug("onStop id=${utteranceId?.take(8)} interrupted=$interrupted → IDLE")
                c?.deliver()
            }

            private fun onEngineError(utteranceId: String?, reason: String) {
                if (utteranceId == null) return
                val c = machine.onEngineError(utteranceId, "Speech error — $reason.")
                _state.value = machine.state
                if (c != null) Log.w(TAG, "onError id=${utteranceId.take(8)} reason=$reason → ERROR")
                c?.deliver()
            }
        })
    }

    private fun codeName(code: Int): String = when (code) {
        TextToSpeech.ERROR_SYNTHESIS -> "synthesis failed"
        TextToSpeech.ERROR_SERVICE -> "service busy"
        TextToSpeech.ERROR_OUTPUT -> "audio output failed"
        TextToSpeech.ERROR_NETWORK -> "network needed"
        TextToSpeech.ERROR_NETWORK_TIMEOUT -> "network timed out"
        TextToSpeech.ERROR_INVALID_REQUEST -> "invalid request"
        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "voice data missing"
        else -> "engine error ($code)"
    }

    private fun pickBestVoice(voices: Set<Voice>): Voice? {
        val en = voices.filter {
            it.locale.language == "en" && !it.isNetworkConnectionRequired
        }
        // Prefer high-quality offline voices, then any offline English voice.
        return en.maxWithOrNull(compareBy({ it.quality }, { it.name.contains("en-US", true) }))
            ?: voices.firstOrNull { it.locale.language == "en" }
    }

    private fun debug(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, "$msg [${machine.state}]")
    }

    companion object {
        private const val TAG = "MioTts"
        private const val INIT_TIMEOUT_MS = 5_000L
    }
}

/**
 * Pure TTS text helpers (top-level and internal so unit tests exercise the
 * real implementations — [MioTts] itself needs Android to construct).
 */

/** Strip anything that sounds bad spoken aloud. */
internal fun sanitize(text: String): String = text
    .replace(Regex("[*_`#]"), "")
    .replace("·", ",")
    .replace(Regex("\\s+"), " ")
    .replace(" ,", ",")
    .trim()
    .take(1500)

/** Split on sentence boundaries, keeping the delimiter with the sentence. */
internal fun splitSentences(text: String): List<String> =
    text.split(Regex("(?<=[.!?;])\\s+")).map { it.trim() }.filter { it.isNotEmpty() }

/** Split into speakable chunks (~200 chars) on sentence boundaries. */
internal fun chunk(text: String, maxLen: Int = 220): List<String> {
    if (text.length <= maxLen) return listOf(text)
    val sentences = splitSentences(text)
    val out = ArrayList<String>()
    val current = StringBuilder()
    for (s in sentences) {
        if (current.length + s.length + 1 > maxLen && current.isNotEmpty()) {
            out += current.toString()
            current.clear()
        }
        if (s.length > maxLen) {
            // Hard-split very long sentences on word boundaries.
            var rest = s
            while (rest.length > maxLen) {
                val cut = rest.lastIndexOf(' ', maxLen).takeIf { it > 80 } ?: maxLen
                out += rest.substring(0, cut).trim()
                rest = rest.substring(cut).trim()
            }
            if (rest.isNotEmpty()) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(rest)
            }
        } else {
            if (current.isNotEmpty()) current.append(' ')
            current.append(s)
        }
    }
    if (current.isNotEmpty()) out += current.toString()
    return out.ifEmpty { listOf(text.take(maxLen)) }
}
