package com.mio.ai.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.mio.ai.BuildConfig
import com.mio.ai.MainActivity
import com.mio.ai.MioApplication
import com.mio.ai.R
import com.mio.ai.ui.vm.AssistantStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Background assistant mode: a microphone-type foreground service that keeps
 * the ONE shared [AssistantCore] pipeline alive while the user is in other
 * apps, hosts the floating orb / voice panel overlay, and shows an honest
 * persistent notification (nothing runs secretly — Stop is one tap away).
 *
 * Lifecycle notes:
 * - START_STICKY: the system restarts us after process death; the overlay
 *   and notification are rebuilt in [onCreate] from persisted settings.
 * - [onTaskRemoved] deliberately keeps us running (user opted in; the
 *   notification explains how to stop).
 * - Screen-off stops voice I/O immediately (mic never records to a dark
 *   room); nothing auto-resumes.
 * - Audio focus loss / phone calls are handled inside [AssistantCore].
 */
class AssistantService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var core: AssistantCore
    private var overlay: FloatingOverlay? = null
    private var notifJob: Job? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                debug("screen off — stopping voice I/O")
                core.stopVoice()
            }
        }
    }
    private var screenReceiverRegistered = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        core = (application as MioApplication).assistant
        createChannel()
        try {
            ServiceCompat.startForeground(
                this,
                NOTIF_ID,
                notification(statusText(AssistantStatus.IDLE)),
                if (Build.VERSION.SDK_INT >= 29) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                } else {
                    0
                },
            )
        } catch (e: Exception) {
            debug("startForeground failed: ${e.message}")
            stopSelf()
            return
        }
        _running.value = true
        overlay = FloatingOverlay(
            context = this,
            core = core,
            scope = serviceScope,
            onOpenApp = { openApp() },
            onOpenPermissions = { highlight -> openPermissions(highlight) },
        ).also { it.ensureOrb() }
        registerScreenOff()
        notifJob = serviceScope.launch {
            core.status.collect { refreshNotification() }
        }
        serviceScope.launch { core.ticker.collect { refreshNotification() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    (application as MioApplication).settingsRepo.setBackgroundAssistant(false)
                }
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TALK -> talk()
            ACTION_OPEN -> openApp()
            else -> overlay?.ensureOrb() // START / sticky restart: (re)build overlay
        }
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        overlay?.reclamp()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Swiping the task away must NOT kill an explicitly-enabled assistant.
        debug("task removed — staying alive (Stop is in the notification)")
    }

    override fun onDestroy() {
        notifJob?.cancel()
        unregisterScreenOff()
        overlay?.destroy()
        overlay = null
        _running.value = false
        serviceScope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- actions

    /** Notification tap / Talk: voice panel over the current app, or the app itself. */
    private fun talk() {
        if (Settings.canDrawOverlays(this)) {
            overlay?.showPanel()
            val s = core.status.value
            if (s == AssistantStatus.IDLE || s == AssistantStatus.ERROR) core.onMicPress()
        } else {
            openApp()
        }
    }

    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
        }
    }

    private fun openPermissions(highlight: String?) {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_DEST, MainActivity.DEST_PERMISSIONS)
                    .putExtra(MainActivity.EXTRA_HIGHLIGHT, highlight),
            )
        }
    }

    // ------------------------------------------------------------- receivers

    private fun registerScreenOff() {
        if (screenReceiverRegistered) return
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(screenOffReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(screenOffReceiver, filter)
        }
        screenReceiverRegistered = true
    }

    private fun unregisterScreenOff() {
        if (!screenReceiverRegistered) return
        runCatching { unregisterReceiver(screenOffReceiver) }
        screenReceiverRegistered = false
    }

    // ---------------------------------------------------------- notification

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.assistant_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.assistant_channel_desc) },
        )
    }

    private fun refreshNotification() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(NOTIF_ID, notification(statusText(core.status.value))) }
    }

    private fun statusText(status: AssistantStatus): String {
        if (!Settings.canDrawOverlays(this)) {
            return "On — floating icon needs permission. Tap to set up."
        }
        return when (status) {
            AssistantStatus.IDLE -> "Floating orb ready — tap to talk"
            AssistantStatus.LISTENING -> "Listening…"
            AssistantStatus.THINKING -> "Thinking…"
            AssistantStatus.SPEAKING -> "Speaking…"
            AssistantStatus.EXECUTING -> core.ticker.value ?: "Working…"
            AssistantStatus.ERROR -> "Needs attention — tap to view"
        }
    }

    private fun notification(text: String): Notification {
        val talkIntent = PendingIntent.getService(
            this, REQ_TALK,
            Intent(this, AssistantService::class.java).setAction(ACTION_TALK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openIntent = PendingIntent.getActivity(
            this, REQ_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this, REQ_STOP,
            Intent(this, AssistantService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.assistant_notification_title))
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_mio_orb)
            .setContentIntent(if (Settings.canDrawOverlays(this)) talkIntent else openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Open Mio", openIntent)
            .addAction(0, "Stop", stopIntent)
        return builder.build()
    }

    private fun debug(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    companion object {
        const val ACTION_START = "com.mio.ai.action.ASSISTANT_START"
        const val ACTION_STOP = "com.mio.ai.action.ASSISTANT_STOP"
        const val ACTION_TALK = "com.mio.ai.action.ASSISTANT_TALK"
        const val ACTION_OPEN = "com.mio.ai.action.ASSISTANT_OPEN"

        private const val TAG = "AssistantService"
        private const val CHANNEL_ID = "mio_assistant"
        private const val NOTIF_ID = 102
        private const val REQ_TALK = 11
        private const val REQ_OPEN = 12
        private const val REQ_STOP = 13

        private val _running = MutableStateFlow(false)

        /** True while the service is actually alive (single-process app). */
        val running: StateFlow<Boolean> = _running.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, AssistantService::class.java).setAction(ACTION_START)
            runCatching {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AssistantService::class.java)) }
        }
    }
}
