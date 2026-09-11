package com.mio.ai.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Text-to-speech with a futuristic-but-warm personality: rate/pitch from
 * settings, best available English voice, sentence chunking for long replies,
 * and per-utterance completion callbacks driving the SPEAKING status.
 */
class MioTts(private val context: Context) {

    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var tts: TextToSpeech? = null
    private var rate = 1.0f
    private var pitch = 1.0f
    @Volatile
    private var preferredVoiceName: String? = null
    private var pendingSpeak: (() -> Unit)? = null
    private val callbacks = ConcurrentHashMap<String, () -> Unit>()
    @Volatile
    private var activeUtterances = 0

    fun init(onDone: (() -> Unit)? = null) {
        if (tts != null) {
            onDone?.invoke()
            return
        }
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                configure()
                _ready.value = true
                pendingSpeak?.invoke()
                pendingSpeak = null
                onDone?.invoke()
            } else {
                _ready.value = false
            }
        }
    }

    fun setRatePitch(rate: Float, pitch: Float) {
        this.rate = rate.coerceIn(0.5f, 2.0f)
        this.pitch = pitch.coerceIn(0.5f, 2.0f)
        tts?.setSpeechRate(this.rate)
        tts?.setPitch(this.pitch)
    }

    /**
     * Use a specific engine voice by [TextToSpeech.getVoices] name.
     * Stored even before init so the preference survives engine startup.
     */
    fun setVoiceByName(name: String): Boolean {
        preferredVoiceName = name.ifBlank { null }
        val tts = this.tts ?: return name.isBlank()
        if (name.isBlank()) {
            pickBestVoice(tts)?.let { tts.voice = it }
            return true
        }
        val voice = runCatching { tts.voices }.getOrNull()?.firstOrNull { it.name == name }
            ?: return false
        return runCatching { tts.setVoice(voice) }.getOrDefault(TextToSpeech.ERROR) ==
            TextToSpeech.SUCCESS
    }

    /** English voice names for the Settings picker (empty until engine ready). */
    fun englishVoiceNames(): List<String> =
        runCatching { tts?.voices }.getOrNull()
            ?.filter { it.locale.language == "en" }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

    /**
     * Speak [text]. Long replies are chunked by sentence so nothing is cut.
     * [onDone] fires when the LAST chunk finishes (or immediately if muted).
     */
    fun speak(text: String, enabled: Boolean, flush: Boolean = true, onDone: (() -> Unit)? = null) {
        val clean = sanitize(text)
        if (clean.isBlank() || !enabled) {
            onDone?.invoke()
            return
        }
        val tts = this.tts
        if (tts == null || _ready.value == false) {
            pendingSpeak = { speak(text, enabled, flush, onDone) }
            init()
            return
        }
        if (flush) {
            tts.stop()
            callbacks.clear()
            activeUtterances = 0
        }
        val chunks = chunk(clean)
        chunks.forEachIndexed { index, part ->
            val id = UUID.randomUUID().toString()
            val isLast = index == chunks.lastIndex
            activeUtterances++
            if (isLast && onDone != null) callbacks[id] = onDone
            val mode = if (index == 0 && flush) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            tts.speak(part, mode, Bundle(), id)
        }
        _speaking.value = true
    }

    fun stop() {
        runCatching { tts?.stop() }
        callbacks.clear()
        activeUtterances = 0
        _speaking.value = false
    }

    fun shutdown() {
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        callbacks.clear()
        _speaking.value = false
        _ready.value = false
    }

    // ----------------------------------------------------------------- helpers

    private fun configure() {
        val tts = this.tts ?: return
        val us = Locale.US
        val result = tts.setLanguage(us)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts.language = Locale.UK
        }
        tts.setSpeechRate(rate)
        tts.setPitch(pitch)
        pickBestVoice(tts)?.let { tts.voice = it }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _speaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                finishUtterance(utteranceId)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                finishUtterance(utteranceId)
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                finishUtterance(utteranceId)
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                finishUtterance(utteranceId)
            }
        })
    }

    private fun finishUtterance(id: String?) {
        if (id != null) callbacks.remove(id)?.invoke()
        if (--activeUtterances <= 0) {
            activeUtterances = 0
            _speaking.value = false
        }
    }

    private fun pickBestVoice(tts: TextToSpeech): Voice? {
        val voices = runCatching { tts.voices }.getOrNull().orEmpty()
        val en = voices.filter {
            it.locale.language == "en" && !it.isNetworkConnectionRequired
        }
        // Prefer high-quality offline voices, then any offline English voice.
        return en.maxWithOrNull(compareBy({ it.quality }, { it.name.contains("en-US", true) }))
            ?: voices.firstOrNull { it.locale.language == "en" }
    }

    /** Strip anything that sounds bad spoken aloud. */
    private fun sanitize(text: String): String = text
        .replace(Regex("[*_`#]"), "")
        .replace("·", ", ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(1500)

    /** Split into speakable chunks (~200 chars) on sentence boundaries. */
    private fun chunk(text: String, maxLen: Int = 220): List<String> {
        if (text.length <= maxLen) return listOf(text)
        val sentences = text.splitAfter('.', '!', '?', ';').map { it.trim() }.filter { it.isNotEmpty() }
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
}
