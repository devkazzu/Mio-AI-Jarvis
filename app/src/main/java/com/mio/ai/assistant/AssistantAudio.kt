package com.mio.ai.assistant

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import com.mio.ai.BuildConfig

/**
 * Audio-focus ownership for the assistant pipeline.
 *
 * - Transient focus is requested before listening and before speaking, and
 *   always abandoned when the pipeline returns to idle (see AssistantCore).
 * - Permanent or transient focus loss (another app, a phone call, an alarm)
 *   immediately stops our voice I/O via [onFocusLost]. We never auto-resume:
 *   stealing the microphone back would be rude and surprising.
 * - [isCallActive] gates listening/speaking during calls using only
 *   [AudioManager.getMode] — no phone-state permission, no call monitoring.
 */
class AssistantAudio(context: Context) {

    private val audioManager: AudioManager? =
        context.applicationContext.getSystemService(AudioManager::class.java)
    private var focusRequest: AudioFocusRequest? = null

    var onFocusLost: (() -> Unit)? = null

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            debug("focus lost (change=$change)")
            onFocusLost?.invoke()
        }
        // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: TTS is short-form speech, not
        // music — we keep playing rather than ducking mid-sentence.
    }

    /** True while a cellular call, ringing, or VoIP call owns the audio path. */
    fun isCallActive(): Boolean {
        return when (audioManager?.mode) {
            AudioManager.MODE_IN_CALL,
            AudioManager.MODE_RINGTONE,
            AudioManager.MODE_IN_COMMUNICATION -> true
            else -> false
        }
    }

    /** Request transient speech focus. Returns false when another app owns audio. */
    fun requestFocus(): Boolean {
        val am = audioManager ?: return false
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = req
        val granted = am.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        debug("requestFocus granted=$granted")
        return granted
    }

    fun abandonFocus() {
        val am = audioManager ?: return
        focusRequest?.let {
            am.abandonAudioFocusRequest(it)
            focusRequest = null
            debug("abandonFocus")
        }
    }

    private fun debug(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    companion object {
        private const val TAG = "AssistantAudio"
    }
}
