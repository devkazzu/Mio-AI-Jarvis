package com.mio.ai.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import com.mio.ai.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-facing text for [SpeechRecognizer] error codes. Pure and unit-tested.
 *
 * Note: self-inflicted errors (our own cancel/stop/destroy tearing down a
 * session, which Android reports as ERROR_CLIENT) never reach this mapping —
 * [SpeechListener] suppresses them by session generation instead of showing
 * a bogus "busy" message.
 */
object SpeechErrorText {
    /**
     * @return (user-facing message, fatal). Non-fatal errors are worth an
     * automatic retry; fatal ones need a settings fix.
     */
    fun messageFor(code: Int): Pair<String, Boolean> = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH ->
            "I didn't catch that — try again?" to false
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
            "I didn't hear anything. Tap the mic when you're ready." to false
        SpeechRecognizer.ERROR_AUDIO ->
            "Microphone error — another app may be using it." to false
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Microphone permission was denied." to true
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "Speech recognition needs a network connection right now." to false
        SpeechRecognizer.ERROR_SERVER ->
            "The speech service had a hiccup — try again." to false
        SpeechRecognizer.ERROR_CLIENT ->
            "Listening was interrupted — try again?" to false
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Speech recognition is busy — another app may be using the microphone." to false
        else -> "Listening failed (code $code)." to false
    }
}

/**
 * Single-shot speech recognition wrapper with live mic level (for the
 * waveform) and partial transcripts. The ViewModel drives the
 * listen → think → speak loop; this class only owns the recognizer.
 *
 * Session discipline (the actual "busy" fix): every start/cancel/destroy
 * retires the previous session by generation. Late callbacks from retired
 * sessions — including the ERROR_CLIENT / ERROR_RECOGNIZER_BUSY that Android
 * delivers after OUR OWN teardown — are silently ignored instead of being
 * shown to the user as a stuck "busy" error. Only errors from the live
 * session reach the UI, and teardown is always explicit, so a new command
 * right after speech just works.
 */
class SpeechListener(private val context: Context) {

    enum class State { IDLE, LISTENING, ERROR }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Mic level 0..1 derived from [RecognitionListener.onRmsChanged]. */
    private val _rms = MutableStateFlow(0f)
    val rms: StateFlow<Float> = _rms.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    /** Delivered once per successful recognition. */
    var onFinalResult: ((String) -> Unit)? = null

    /** User-friendly error message when recognition fails or can't start. */
    var onError: ((message: String, fatal: Boolean) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var sessionListener: Listener? = null

    /** Current session id; bumped on every start. */
    private var generation = 0L

    /** Errors from this session are our own teardown — suppress silently. */
    private var suppressed = -1L

    /** A terminal callback was already delivered for this session. */
    private var finished = -1L

    @Volatile
    private var lastErrorAt = 0L

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts one recognition pass, retiring any previous session first.
     * Returns false when it can't start (no permission / no recognizer /
     * engine creation failed) — the caller routes to Permissions UI.
     */
    fun startListening(): Boolean {
        if (!hasPermission() || !isAvailable()) return false
        val myGen: Long
        val sr: SpeechRecognizer
        synchronized(this) {
            // Retire the old session silently: its late callbacks go stale by generation.
            suppressed = generation
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            sessionListener = null
            generation += 1
            myGen = generation
            debug("start gen=$myGen")
            sr = try {
                SpeechRecognizer.createSpeechRecognizer(context)
            } catch (e: Exception) {
                _state.value = State.ERROR
                Log.w(TAG, "gen=$myGen create failed: ${e.message}")
                onError?.invoke("Couldn't start listening.", true)
                return false
            }
            recognizer = sr
            val listener = Listener(myGen)
            sessionListener = listener
            sr.setRecognitionListener(listener)
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            // Keep the pass open a little longer for multi-step commands.
            putExtra("android.speech.extra.DICTATION_MODE", true)
        }
        _partial.value = ""
        _rms.value = 0f
        _state.value = State.LISTENING
        return try {
            sr.startListening(intent)
            true
        } catch (e: Exception) {
            synchronized(this) {
                if (recognizer === sr) {
                    recognizer = null
                    sessionListener = null
                }
            }
            _state.value = State.ERROR
            Log.w(TAG, "gen=$myGen start failed: ${e.message}")
            onError?.invoke("Couldn't start listening.", true)
            false
        }
    }

    fun stopListening() {
        synchronized(this) {
            suppressed = generation
            debug("stop gen=$generation (errors suppressed)")
            runCatching { recognizer?.stopListening() }
        }
    }

    /** User-initiated cancel: the session ends silently (no error shown). */
    fun cancel() {
        synchronized(this) {
            suppressed = generation
            debug("cancel gen=$generation (errors suppressed)")
            runCatching { recognizer?.cancel() }
        }
        _state.value = State.IDLE
        _rms.value = 0f
    }

    fun destroy() {
        synchronized(this) {
            suppressed = generation
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            sessionListener = null
        }
        _state.value = State.IDLE
    }

    private fun debug(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    private inner class Listener(private val gen: Long) : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (isLive()) _state.value = State.LISTENING
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onEndOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            if (!isLive()) return
            // rmsdB is roughly 0..10 — normalize with a little headroom.
            _rms.value = (rmsdB / 9f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onPartialResults(results: Bundle?) {
            if (!isLive()) return
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) _partial.value = text
        }

        override fun onResults(results: Bundle?) {
            if (!claimTerminal()) return
            _state.value = State.IDLE
            _rms.value = 0f
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            destroyCurrent()
            if (text.isNotBlank()) {
                onFinalResult?.invoke(text)
            } else {
                onError?.invoke("I didn't catch that — try again?", false)
            }
        }

        override fun onError(code: Int) {
            if (!claimTerminal()) {
                debug("onError gen=$gen code=$code ignored (stale/suppressed)")
                return
            }
            _state.value = State.IDLE
            _rms.value = 0f
            destroyCurrent()
            // Debounce flurries (some engines emit ERROR_NO_MATCH repeatedly).
            val now = SystemClock.uptimeMillis()
            if (now - lastErrorAt < 600 && code == SpeechRecognizer.ERROR_NO_MATCH) {
                debug("onError gen=$gen code=$code debounced")
                return
            }
            lastErrorAt = now
            val (message, fatal) = SpeechErrorText.messageFor(code)
            if (fatal) Log.w(TAG, "gen=$gen code=$code fatal") else debug("onError gen=$gen code=$code")
            onError?.invoke(message, fatal)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        /** True when this callback belongs to the live, non-suppressed session. */
        private fun isLive(): Boolean = synchronized(this@SpeechListener) {
            gen == generation && gen != suppressed
        }

        /**
         * Claim the single terminal callback (results/error) for this
         * session. Late, duplicate, stale, or self-inflicted terminals lose.
         */
        private fun claimTerminal(): Boolean = synchronized(this@SpeechListener) {
            if (gen != generation || gen == suppressed || gen == finished) return false
            finished = gen
            true
        }

        private fun destroyCurrent() {
            synchronized(this@SpeechListener) {
                if (sessionListener === this) {
                    runCatching { recognizer?.destroy() }
                    recognizer = null
                    sessionListener = null
                }
            }
        }
    }

    companion object {
        private const val TAG = "MioStt"
    }
}
