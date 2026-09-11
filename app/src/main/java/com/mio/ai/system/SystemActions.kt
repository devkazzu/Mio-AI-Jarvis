package com.mio.ai.system

import android.Manifest
import android.app.NotificationManager
import android.app.SearchManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.mio.ai.core.actions.VolumeDirection
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Every direct device capability: torch, Wi-Fi, Bluetooth, volume, DND,
 * brightness, alarms, timers, camera, calls, SMS composer, web search,
 * navigation and read-only status queries.
 *
 * Each method returns an [OpResult] that either confirms success or explains
 * exactly which permission / user action is required — Mio never pretends an
 * impossible action worked.
 */
class SystemActions(private val context: Context) {

    private val contacts = ContactsResolver(context)

    @Volatile
    private var torchOn = false
    fun isTorchOn(): Boolean = torchOn

    // ------------------------------------------------------------------ torch

    fun setTorch(on: Boolean): OpResult {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            return OpResult.fail("This device has no flashlight.", "NOT_SUPPORTED")
        }
        if (!hasPermission(Manifest.permission.CAMERA)) {
            return OpResult.fail(
                "Camera permission is needed to control the flashlight.",
                "PERMISSION_DENIED", "camera",
            )
        }
        return try {
            val cm = context.getSystemService(CameraManager::class.java)
                ?: return OpResult.fail("Camera service is unavailable.", "NOT_SUPPORTED")
            val id = cm.cameraIdList.firstOrNull { camId ->
                runCatching {
                    cm.getCameraCharacteristics(camId)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
                }.getOrDefault(false)
            } ?: return OpResult.fail("No flash unit found on this device.", "NOT_SUPPORTED")
            cm.setTorchMode(id, on)
            torchOn = on
            OpResult.ok(if (on) "Flashlight is on." else "Flashlight is off.")
        } catch (_: SecurityException) {
            OpResult.fail(
                "Camera permission is needed to control the flashlight.",
                "PERMISSION_DENIED", "camera",
            )
        } catch (e: Exception) {
            OpResult.fail("Couldn't reach the flashlight (${e.message}).", "UNKNOWN")
        }
    }

    // -------------------------------------------------------------------- wifi

    fun isWifiOn(): Boolean? = runCatching {
        context.applicationContext.getSystemService(WifiManager::class.java)?.isWifiEnabled
    }.getOrNull()

    fun setWifi(on: Boolean): OpResult {
        val wm = context.applicationContext.getSystemService(WifiManager::class.java)
            ?: return OpResult.fail("Wi-Fi service is unavailable.", "NOT_SUPPORTED")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return try {
                @Suppress("DEPRECATION")
                wm.isWifiEnabled = on
                OpResult.ok(if (on) "Wi-Fi is on." else "Wi-Fi is off.")
            } catch (_: SecurityException) {
                OpResult.fail("The system blocked the Wi-Fi switch.", "PERMISSION_DENIED", "wifi")
            }
        }
        // Android 10+: silent toggles are reserved for system apps.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            openSettingsPanel(Settings.Panel.ACTION_WIFI, Settings.ACTION_WIFI_SETTINGS)
        } else {
            openSettingsPanel(Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_WIFI_SETTINGS)
        }
        return OpResult.ok(
            if (on) "Android needs the tap for this one — I opened the Wi-Fi panel so you can switch it on."
            else "Android needs the tap for this one — I opened the Wi-Fi panel so you can switch it off.",
        )
    }

    // --------------------------------------------------------------- bluetooth

    fun isBluetoothOn(): Boolean? = runCatching {
        if (Build.VERSION.SDK_INT >= 31 && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return null
        }
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
    }.getOrNull()

    fun setBluetooth(on: Boolean): OpResult {
        if (Build.VERSION.SDK_INT >= 31 && !hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
            return OpResult.fail("Bluetooth permission is needed.", "PERMISSION_DENIED", "bluetooth")
        }
        val adapter = try {
            context.getSystemService(BluetoothManager::class.java)?.adapter
        } catch (_: SecurityException) {
            null
        } ?: return OpResult.fail("No Bluetooth adapter found.", "NOT_SUPPORTED")
        return try {
            @Suppress("DEPRECATION")
            val ok = if (on) adapter.enable() else adapter.disable()
            if (!ok) {
                OpResult.fail(
                    "The system refused the Bluetooth switch — flip it in Quick Settings.",
                    "NOT_SUPPORTED",
                )
            } else {
                OpResult.ok(if (on) "Bluetooth is on." else "Bluetooth is off.")
            }
        } catch (_: SecurityException) {
            OpResult.fail("Bluetooth permission is needed.", "PERMISSION_DENIED", "bluetooth")
        }
    }

    // ------------------------------------------------------------------ volume

    fun volumePercent(): Int = runCatching {
        val am = context.getSystemService(AudioManager::class.java)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).roundToInt()
    }.getOrDefault(-1)

    fun adjustVolume(direction: VolumeDirection, percent: Int?): OpResult {
        val am = context.getSystemService(AudioManager::class.java)
        return try {
            if (percent != null) {
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                val value = (max * percent / 100f).roundToInt().coerceIn(0, max)
                if (value > 0) {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                }
                am.setStreamVolume(AudioManager.STREAM_MUSIC, value, AudioManager.FLAG_SHOW_UI)
                return OpResult.ok("Volume set to $percent%.")
            }
            val adjust = when (direction) {
                VolumeDirection.UP -> AudioManager.ADJUST_RAISE
                VolumeDirection.DOWN -> AudioManager.ADJUST_LOWER
                VolumeDirection.MUTE -> AudioManager.ADJUST_MUTE
                VolumeDirection.UNMUTE -> AudioManager.ADJUST_UNMUTE
            }
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjust, AudioManager.FLAG_SHOW_UI)
            OpResult.ok(
                when (direction) {
                    VolumeDirection.UP -> "Volume up."
                    VolumeDirection.DOWN -> "Volume down."
                    VolumeDirection.MUTE -> "Muted."
                    VolumeDirection.UNMUTE -> "Sound back on."
                },
            )
        } catch (e: Exception) {
            OpResult.fail("Couldn't change the volume (${e.message}).", "UNKNOWN")
        }
    }

    // --------------------------------------------------------------------- dnd

    fun isDndOn(): Boolean? = runCatching {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) return null
        nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
    }.getOrNull()

    fun setDnd(on: Boolean): OpResult {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            return OpResult.fail(
                "Do Not Disturb needs one-time access — tap Fix to allow it.",
                "PERMISSION_DENIED", "dnd",
            )
        }
        nm.setInterruptionFilter(
            if (on) NotificationManager.INTERRUPTION_FILTER_NONE
            else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        return OpResult.ok(if (on) "Do Not Disturb is on. Shhh." else "Do Not Disturb is off.")
    }

    // -------------------------------------------------------------- brightness

    fun setBrightness(percent: Int?, up: Boolean?): OpResult {
        if (!Settings.System.canWrite(context)) {
            return OpResult.fail(
                "I need permission to change brightness — tap Fix to allow it.",
                "PERMISSION_DENIED", "write_settings",
            )
        }
        return try {
            val resolver = context.contentResolver
            val current = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val target = when {
                percent != null -> (percent * 255 / 100).coerceIn(5, 255)
                up == true -> (current + 38).coerceAtMost(255)
                up == false -> (current - 38).coerceAtLeast(5)
                else -> current
            }
            Settings.System.putInt(
                resolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL,
            )
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, target)
            OpResult.ok("Brightness set to ${target * 100 / 255}%.")
        } catch (_: SecurityException) {
            OpResult.fail(
                "I need permission to change brightness — tap Fix to allow it.",
                "PERMISSION_DENIED", "write_settings",
            )
        }
    }

    // ---------------------------------------------------------- alarms/timers

    fun setAlarm(hour: Int, minute: Int, label: String?): OpResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        if (!label.isNullOrBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        val spoken = LocalTime.of(hour, minute)
            .format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        return fire(intent, "Alarm set for $spoken.")
    }

    fun showAlarms(): OpResult =
        fire(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Opening your alarms.")

    fun listApps(): OpResult =
        fire(Intent(Settings.ACTION_APPLICATION_SETTINGS), "Showing your installed apps.")

    fun setTimer(seconds: Int, label: String?): OpResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        if (!label.isNullOrBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return fire(intent, "Timer set for ${speakDuration(seconds)}.")
    }

    // ------------------------------------------------------------------ camera

    fun openCamera(): OpResult {
        val still = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        if (canHandle(still)) return fire(still, "Opening the camera.")
        return fire(Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Opening the camera.")
    }

    // ------------------------------------------------------- calls & messages
    // NOTE: Android only lets the dialer/SMS app place calls & send texts.
    // Mio opens them pre-filled — the user confirms with one tap. This is
    // deliberate and disclosed in the confirmation prompt.

    fun dial(recipient: String): OpResult {
        val resolved = contacts.resolve(recipient)
        if (resolved.permissionNeeded) {
            return OpResult.fail(
                "I need contacts access to find $recipient.",
                "PERMISSION_DENIED", "contacts",
            )
        }
        val hit = resolved.hit
            ?: return OpResult.fail("I couldn't find $recipient in your contacts.", "CONTACT_NOT_FOUND")
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", hit.number, null))
        return fire(intent, "Calling ${hit.name} — tap the call button to connect.")
    }

    fun composeSms(recipient: String, body: String?): OpResult {
        val resolved = contacts.resolve(recipient)
        if (resolved.permissionNeeded) {
            return OpResult.fail(
                "I need contacts access to find $recipient.",
                "PERMISSION_DENIED", "contacts",
            )
        }
        val hit = resolved.hit
            ?: return OpResult.fail("I couldn't find $recipient in your contacts.", "CONTACT_NOT_FOUND")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", hit.number, null))
        if (!body.isNullOrBlank()) intent.putExtra("sms_body", body)
        return fire(intent, "Opening a message to ${hit.name}.")
    }

    // -------------------------------------------------------------- web & maps

    fun webSearch(query: String): OpResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)
        if (canHandle(intent)) return fire(intent, "Searching the web for $query.")
        val fallback = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://www.google.com/search?q=${Uri.encode(query)}"),
        )
        return fire(fallback, "Searching the web for $query.")
    }

    fun navigate(destination: String): OpResult {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(destination)}")
        val maps = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        if (canHandle(maps)) return fire(maps, "Navigating to $destination.")
        return fire(Intent(Intent.ACTION_VIEW, uri), "Navigating to $destination.")
    }

    // ------------------------------------------------------------------ queries

    fun queryTime(): OpResult {
        val t = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        return OpResult.ok("It's $t.")
    }

    fun queryDate(): OpResult {
        val d = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US))
        return OpResult.ok("Today is $d.")
    }

    fun queryBattery(): OpResult {
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val charging = runCatching { bm.isCharging }.getOrDefault(false)
        if (pct < 0) return OpResult.fail("Battery info is unavailable.", "NOT_SUPPORTED")
        return OpResult.ok(
            if (charging) "Battery is at $pct% and charging."
            else "Battery is at $pct%.",
        )
    }

    fun queryDeviceStatus(): OpResult {
        val parts = ArrayList<String>()
        when (isWifiOn()) {
            true -> parts += "Wi-Fi is on"
            false -> parts += "Wi-Fi is off"
            null -> parts += "Wi-Fi state unknown"
        }
        when (isBluetoothOn()) {
            true -> parts += "Bluetooth is on"
            false -> parts += "Bluetooth is off"
            null -> parts += "Bluetooth state unknown"
        }
        parts += if (torchOn) "flashlight is on" else "flashlight is off"
        when (isDndOn()) {
            true -> parts += "Do Not Disturb is on"
            false -> parts += "Do Not Disturb is off"
            null -> Unit // access not granted — don't nag in a status readout
        }
        val vol = volumePercent()
        if (vol >= 0) parts += "volume at $vol%"
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        if (pct >= 0) parts += "battery at $pct%"
        return OpResult.ok(parts.joinToString(", ").replaceFirstChar { it.uppercase() } + ".")
    }

    // ----------------------------------------------------------------- helpers

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun openSettingsPanel(panelAction: String, fallbackAction: String) {
        try {
            context.startActivity(Intent(panelAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            runCatching {
                context.startActivity(Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun canHandle(intent: Intent): Boolean =
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null

    private fun fire(intent: Intent, successDetail: String): OpResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (!canHandle(intent)) {
                return OpResult.fail("No app on this phone can do that.", "NOT_SUPPORTED")
            }
            context.startActivity(intent)
            OpResult.ok(successDetail)
        } catch (e: Exception) {
            OpResult.fail("That didn't open (${e.message}).", "UNKNOWN")
        }
    }

    private fun speakDuration(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return buildList {
            if (h > 0) add("$h hour${if (h > 1) "s" else ""}")
            if (m > 0) add("$m minute${if (m > 1) "s" else ""}")
            if (s > 0 || isEmpty()) add("$s second${if (s != 1) "s" else ""}")
        }.joinToString(" ")
    }
}
