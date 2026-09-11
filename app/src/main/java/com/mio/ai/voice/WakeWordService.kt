package com.mio.ai.voice

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mio.ai.MainActivity
import com.mio.ai.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Optional always-on "Hey Mio" listener (user toggle in Settings).
 *
 * Honest engineering note: without a dedicated keyword-spotting model this
 * loops Android's SpeechRecognizer and matches the wake phrase in partial
 * results. That works on-device but costs battery — so it is OFF by default,
 * backs off on errors, and the notification makes it visible while running.
 */
class WakeWordService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var listener: SpeechListener? = null
    private var failures = 0
    private var restartJob: Job? = null
    private var cooldownUntil = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification(),
                if (Build.VERSION.SDK_INT >= 29) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            stopSelf()
            return
        }
        startLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onDestroy() {
        restartJob?.cancel()
        runCatching { listener?.destroy() }
        listener = null
        scope.cancel()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ loop

    private fun startLoop() {
        val l = SpeechListener(this).also { listener = it }
        l.onFinalResult = { text ->
            failures = 0
            if (isWakePhrase(text) && SystemClock.uptimeMillis() > cooldownUntil) {
                cooldownUntil = SystemClock.uptimeMillis() + COOLDOWN_MS
                openAssistant()
            }
            scheduleRestart(400)
        }
        l.onError = { _, _ ->
            failures++
            scheduleRestart((500L * failures).coerceAtMost(8_000L))
        }
        if (!l.startListening()) {
            // No permission / recognizer — nothing to do until settings change.
            stopSelf()
            return
        }
        // Partial results double the wake chances without waiting for finals.
        scope.launch {
            // Poll partial flow lightly; SpeechListener owns the recognizer.
            var last = ""
            while (true) {
                delay(500)
                val p = l.partial.value
                if (p != last && p.isNotBlank()) {
                    last = p
                    failures = 0
                    if (isWakePhrase(p) && SystemClock.uptimeMillis() > cooldownUntil) {
                        cooldownUntil = SystemClock.uptimeMillis() + COOLDOWN_MS
                        openAssistant()
                        l.cancel()
                        scheduleRestart(6_000)
                        return@launch
                    }
                }
            }
        }
    }

    private fun scheduleRestart(afterMs: Long) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(afterMs)
            try {
                listener?.startListening()
            } catch (_: Exception) {
                failures++
                scheduleRestart((500L * failures).coerceAtMost(8_000L))
            }
        }
    }

    private fun openAssistant() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_WAKE, true)
        runCatching { startActivity(intent) }
    }

    // ------------------------------------------------------------ notification

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.wake_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.wake_channel_desc)
            },
        )
    }

    private fun notification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.wake_notification_title))
            .setContentText(getString(R.string.wake_notification_text))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(open)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val ACTION_START = "com.mio.ai.action.START_WAKE"
        const val ACTION_STOP = "com.mio.ai.action.STOP_WAKE"
        private const val CHANNEL_ID = "mio_wake"
        private const val NOTIF_ID = 101
        private const val COOLDOWN_MS = 8_000L

        private val WAKE = Regex("(?i)\\bm[i!1]o\\b|hey m|ok m[i!1]o")

        fun isWakePhrase(text: String): Boolean = WAKE.containsMatchIn(text)

        fun start(context: Context) {
            val intent = Intent(context, WakeWordService::class.java).setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, WakeWordService::class.java)) }
        }
    }
}
