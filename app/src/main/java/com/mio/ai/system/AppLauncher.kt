package com.mio.ai.system

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import com.mio.ai.accessibility.AccessibilityController
import com.mio.ai.core.commands.AppCatalog
import kotlinx.coroutines.delay

/**
 * Voice app launching: curated catalog → fuzzy launcher search → honest miss.
 * Plus foreground-app closing via the accessibility bridge.
 */
class AppLauncher(private val context: Context) {

    fun launch(query: String, packageName: String? = null): OpResult {
        if (!packageName.isNullOrBlank()) return launchPackage(packageName, query)

        val q = AppCatalog.normalize(query)
        specialLaunch(q)?.let { return it }

        AppCatalog.find(query)?.let { return launchPackage(it.packageName, it.displayName) }

        fuzzyLaunch(query)?.let { return it }

        return OpResult.fail(
            "I couldn't find an app called $query. Check the name and try again.",
            "APP_NOT_FOUND",
        )
    }

    /** Back out of the foreground app, then drop to Home. */
    suspend fun closeCurrentApp(): OpResult {
        if (!AccessibilityController.isConnected.value) {
            return OpResult.fail(
                "Turn on Mio's UI Control to close apps.",
                "SERVICE_DISABLED", "accessibility",
            )
        }
        val start = AccessibilityController.foregroundPackage.value
        var left = false
        repeat(6) {
            if (!AccessibilityController.pressBack()) return@repeat
            delay(450)
            val now = AccessibilityController.foregroundPackage.value
            if (start != null && now != null && now != start) {
                left = true
                return@repeat
            }
            if (left) return@repeat
        }
        AccessibilityController.goHome()
        return if (left || start == null) {
            OpResult.ok("Closed.")
        } else {
            // Never left the start package — probably already on the launcher.
            OpResult.ok("There's no app to close — you're on the Home screen.")
        }
    }

    // ----------------------------------------------------------------- helpers

    private fun specialLaunch(normalized: String): OpResult? = when (normalized) {
        "settings", "phone settings", "system settings" ->
            fire(Intent(Settings.ACTION_SETTINGS), "Opening Settings.")

        "play store", "google play", "playstore" ->
            launchPackage("com.android.vending", "Play Store")

        "camera" ->
            fire(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA), "Opening the camera.")

        "clock" ->
            fire(Intent(AlarmClock.ACTION_SHOW_ALARMS), "Opening the clock.")

        "phone", "dialer", "phone app" ->
            fire(Intent(Intent.ACTION_DIAL), "Opening the phone.")

        "messages", "messaging", "sms", "text messages" ->
            fire(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING), "Opening messages.")

        "browser", "web browser", "internet" ->
            fire(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER), "Opening the browser.")

        "gallery" ->
            fire(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY), "Opening the gallery.")

        "email", "mail" ->
            fire(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL), "Opening email.")

        "maps" ->
            AppCatalog.find("Google Maps")?.let { launchPackage(it.packageName, it.displayName) }

        else -> null
    }

    private fun launchPackage(packageName: String, label: String): OpResult {
        val intent = try {
            context.packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: Exception) {
            null
        } ?: return OpResult.fail("$label isn't installed or can't be opened.", "APP_NOT_FOUND")
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            OpResult.ok("Opening $label.")
        } catch (e: Exception) {
            OpResult.fail("Couldn't open $label (${e.message}).", "UNKNOWN")
        }
    }

    /** Fuzzy search across every launchable app (visible via the LAUNCHER <queries> block). */
    private fun fuzzyLaunch(query: String): OpResult? {
        val q = AppCatalog.normalize(query)
        if (q.length < 2) return null
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = try {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.queryIntentActivities(main, PackageManager.ResolveInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(main, 0)
            }
        } catch (_: Exception) {
            return null
        }
        var bestLabel: String? = null
        var bestPkg: String? = null
        var bestScore = 0
        for (r in apps) {
            val label = runCatching { r.loadLabel(pm).toString() }.getOrNull() ?: continue
            val pkg = r.activityInfo?.packageName ?: continue
            val score = maxOf(
                scoreName(q, AppCatalog.normalize(label)),
                scoreName(q, AppCatalog.normalize(pkg.substringAfterLast('.'))),
            )
            if (score > bestScore) {
                bestScore = score
                bestLabel = label
                bestPkg = pkg
            }
        }
        if (bestPkg != null && bestScore >= 55) {
            return launchPackage(bestPkg, bestLabel ?: query)
        }
        return null
    }

    private fun scoreName(query: String, name: String): Int {
        if (query == name) return 100
        if (name.startsWith(query)) return 85
        val qTokens = query.split(' ').filter { it.length > 1 }.toSet()
        val nTokens = name.split(' ').filter { it.length > 1 }.toSet()
        if (qTokens.isNotEmpty() && nTokens.isNotEmpty()) {
            val overlap = (qTokens intersect nTokens).size
            if (overlap == qTokens.size) return 75
            if (overlap > 0) return 60
        }
        if (name.contains(query)) return 55
        return 0
    }

    @Suppress("DEPRECATION")
    private fun fire(intent: Intent, successDetail: String): OpResult {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) == null) {
                return OpResult.fail("No app on this phone can do that.", "NOT_SUPPORTED")
            }
            context.startActivity(intent)
            OpResult.ok(successDetail)
        } catch (e: Exception) {
            OpResult.fail("That didn't open (${e.message}).", "UNKNOWN")
        }
    }
}
