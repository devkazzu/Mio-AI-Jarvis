package com.mio.ai.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single-shot speech recognition wrapper with live mic level (for the
 * waveform) and partial transcripts. The ViewModel drives the
 * listen → think → speak loop; this class only owns the recognizer.
 */
class SpeechListener(private val context: Context) {

    enum class State { IDLE, LISTENING, ERROR }

    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Mic level 0..1 derived from [RecognitionListener.onRmsChanged]. */
    private val _rms01 = MutableStateFlow(0f)
    val rms01: StateFlow<Float> = _rms01.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    /** Delivered once per successful recognition. */
    var onFinalResult: ((String) -> Unit)? = null

    /** User-friendly error message when recognition fails or can't start. */
    var onError: ((message: String, fatal: Boolean) -> Unit)? = null

    private var recognizer: SpeechRecognizer? = null
    private var lastErrorAt = 0L

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun isAvailable(): Boolean =
        SpeechRecognizer.isRecognitionAvailable(context)

    /**
     * Starts one recognition pass. Returns false when it can't start
     * (no permission / no recognizer) — the caller routes to Permissions UI.
     */
    fun startListening(): Boolean {
        if (!hasPermission() || !isAvailable()) return false
        try {
            destroyRecognizer()
            val sr = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer = sr
            sr.setRecognitionListener(Listener())
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
            _rms01.value = 0f
            _state.value = State.LISTENING
            sr.startListening(intent)
            return true
        } catch (e: Exception) {
            _state.value = State.ERROR
            onError?.invoke("Couldn't start listening (${e.message}).", true)
            return false
        }
    }

    fun stopListening() {
        runCatching { recognizer?.stopListening() }
    }

    fun cancel() {
        runCatching { recognizer?.cancel() }
        _state.value = State.IDLE
        _rms01.value = 0f
    }

    fun destroy() {
        destroyRecognizer()
        _state.value = State.IDLE
    }

    private fun destroyRecognizer() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private inner class Listener : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            _state.value = State.LISTENING
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onEndOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // rmsdB is roughly 0..10 — normalize with a little headroom.
            _rms01.value = (rmsdB / 9f).coerceIn(0f, 1f)
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onPartialResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull().orEmpty()
            if (text.isNotBlank()) _partial.value = text
        }

        override fun onResults(results: Bundle?) {
            _state.value = State.IDLE
            _rms01.value = 0f
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim().orEmpty()
            if (text.isNotBlank()) {
                onFinalResult?.invoke(text)
            } else {
                onError?.invoke("I didn't catch that — try again?", false)
            }
            destroyRecognizer()
        }

        override fun onError(code: Int) {
            _state.value = State.IDLE
            _rms01.value = 0f
            destroyRecognizer()
            // Debounce flurries (some engines emit ERROR_NO_MATCH repeatedly).
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastErrorAt < 600 && code == SpeechRecognizer.ERROR_NO_MATCH) return
            lastErrorAt = now
            val (message, fatal) = when (code) {
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
                SpeechRecognizer.ERROR_CLIENT, SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                    "Speech engine is busy — one moment." to false
                else -> "Listening failed (code $code)." to false
            }
            onError?.invoke(message, fatal)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }
}
