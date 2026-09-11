package com.mio.ai.ui.screens

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mio.ai.accessibility.AccessibilityController
import com.mio.ai.ui.components.GlassSurface
import com.mio.ai.ui.components.HudBackground
import com.mio.ai.ui.components.InfoNote
import com.mio.ai.ui.components.MioDivider
import com.mio.ai.ui.components.MioTopBar
import com.mio.ai.ui.components.PanelAccent
import com.mio.ai.ui.components.PermissionCard
import com.mio.ai.ui.components.PrimaryButton
import com.mio.ai.ui.components.SectionHeader
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens

private data class PermRow(
    val id: String,
    val title: String,
    val why: String,
    val granted: Boolean,
    val infoOnly: Boolean = false,
    val actionLabel: String = "Allow",
    val onAction: (() -> Unit)? = null,
)

/**
 * Capabilities, honestly: a hero panel explaining UI Control, one card per
 * permission with a plain-language "why", and a footer of OS limitations.
 * Nothing Mio needs is ever hidden.
 */
@Composable
fun PermissionsScreen(highlight: String?, onBack: () -> Unit) {
    val mio = mioColors
    val dim = mioDimens
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { tick++ }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) tick++
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val rows = remember(tick) { buildRows(ctx) { perm -> runtimeLauncher.launch(perm) } }
    val a11yGranted = remember(tick) {
        AccessibilityController.isServiceEnabledInSettings(ctx) ||
            AccessibilityController.isConnected.value
    }
    val readyCount = rows.count { it.granted || it.infoOnly }

    Box(Modifier.fillMaxSize()) {
        HudBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = dim.gutter),
        ) {
            MioTopBar(title = "Access", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(dim.md),
            ) {
                item {
                    Text(
                        "$readyCount OF ${rows.size} READY",
                        style = CaptionMono,
                        color = mio.textSecondary,
                    )
                }
                // -- Hero: UI Control ------------------------------------------------
                item {
                    GlassSurface(
                        accent = if (a11yGranted) PanelAccent.SUCCESS else PanelAccent.CYAN,
                    ) {
                        Column {
                            Text(
                                if (a11yGranted) "UI CONTROL IS ON" else "WHY MIO NEEDS ACCESS",
                                style = CaptionMono,
                                color = if (a11yGranted) mio.success else mio.accent,
                            )
                            Spacer(Modifier.height(dim.sm))
                            Text(
                                "Accessibility access allows Mio to understand and interact with " +
                                    "buttons, text fields and other controls inside supported apps.",
                                style = MioTypography.bodyLarge,
                                color = mio.textPrimary,
                            )
                            Spacer(Modifier.height(dim.sm))
                            Text(
                                "Mio only ever acts on your explicit commands — never in the " +
                                    "background, never on its own. Nothing you see or type leaves " +
                                    "this phone because of it.",
                                style = MioTypography.bodyMedium,
                                color = mio.textSecondary,
                            )
                            Spacer(Modifier.height(dim.md))
                            if (!a11yGranted) {
                                PrimaryButton(
                                    text = "Enable Accessibility",
                                    onClick = {
                                        openScreen(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        tick++
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                Text(
                                    "Enabled — try “open YouTube, search for lo-fi beats”.",
                                    style = CaptionMono,
                                    color = mio.success,
                                )
                            }
                        }
                    }
                }
                item { SectionHeader(title = "Permissions") }
                items(rows, key = { it.id }) { row ->
                    PermissionCard(
                        title = row.title,
                        why = row.why,
                        granted = row.granted,
                        infoOnly = row.infoOnly,
                        ctaLabel = row.actionLabel,
                        highlighted = row.id == highlight,
                        onCta = row.onAction?.let { action -> { action(); tick++ } },
                    )
                }
                item {
                    MioDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = dim.sm))
                }
                item {
                    InfoNote(
                        "The floating icon uses draw-over-apps only while Background assistant " +
                            "is on (Settings → Background). Turn it off and the icon is gone.",
                    )
                }
                item {
                    InfoNote(
                        "Some limits come from Android itself: calls and texts always need " +
                            "your final tap, and Wi-Fi needs the system panel on Android 10+.",
                    )
                }
                item { Spacer(Modifier.height(dim.xl)) }
            }
        }
    }
}

// ------------------------------------------------------------------ state

private fun hasPerm(ctx: Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

private fun openScreen(ctx: Context, action: String, withPackage: Boolean = false) {
    val intent = Intent(action).apply {
        if (withPackage) data = Uri.parse("package:${ctx.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { ctx.startActivity(intent) }
}

private fun buildRows(ctx: Context, request: (String) -> Unit): List<PermRow> {
    val notifNeeded = Build.VERSION.SDK_INT >= 33
    val btNeeded = Build.VERSION.SDK_INT >= 31
    return listOf(
        PermRow(
            id = "mic",
            title = "Microphone",
            why = "Hears your voice commands and the optional wake word.",
            granted = hasPerm(ctx, Manifest.permission.RECORD_AUDIO),
            onAction = { request(Manifest.permission.RECORD_AUDIO) },
        ),
        PermRow(
            id = "notifications",
            title = "Notifications",
            why = if (notifNeeded) {
                "Shows the wake-word listener and background assistant while they run."
            } else {
                "Not required below Android 13."
            },
            granted = if (notifNeeded) hasPerm(ctx, Manifest.permission.POST_NOTIFICATIONS) else true,
            infoOnly = !notifNeeded,
            onAction = if (notifNeeded) {
                { request(Manifest.permission.POST_NOTIFICATIONS) }
            } else {
                null
            },
        ),
        PermRow(
            id = "camera",
            title = "Camera",
            why = "Only switches the flashlight torch. Never takes photos.",
            granted = hasPerm(ctx, Manifest.permission.CAMERA),
            onAction = { request(Manifest.permission.CAMERA) },
        ),
        PermRow(
            id = "bluetooth",
            title = "Bluetooth",
            why = if (btNeeded) {
                "Voice toggles for Bluetooth on and off."
            } else {
                "Included on this Android version."
            },
            granted = if (btNeeded) hasPerm(ctx, Manifest.permission.BLUETOOTH_CONNECT) else true,
            infoOnly = !btNeeded,
            onAction = if (btNeeded) {
                { request(Manifest.permission.BLUETOOTH_CONNECT) }
            } else {
                null
            },
        ),
        PermRow(
            id = "contacts",
            title = "Contacts",
            why = "Resolves “call mom” to the right phone number.",
            granted = hasPerm(ctx, Manifest.permission.READ_CONTACTS),
            onAction = { request(Manifest.permission.READ_CONTACTS) },
        ),
        PermRow(
            id = "write_settings",
            title = "System settings",
            why = "Screen brightness control. Granted in system Settings.",
            granted = Settings.System.canWrite(ctx),
            actionLabel = "Open Settings",
            onAction = { openScreen(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS, withPackage = true) },
        ),
        PermRow(
            id = "dnd",
            title = "Do Not Disturb",
            why = "Silence and unsilence the phone by voice. Granted in system Settings.",
            granted = ctx.getSystemService(NotificationManager::class.java)
                ?.isNotificationPolicyAccessGranted == true,
            actionLabel = "Open Settings",
            onAction = { openScreen(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
        ),
        PermRow(
            id = "overlay",
            title = "Display over other apps",
            why = "Floating Mio icon. Only used while Background assistant is on.",
            granted = Settings.canDrawOverlays(ctx),
            actionLabel = "Open Settings",
            onAction = { openScreen(ctx, Settings.ACTION_MANAGE_OVERLAY_PERMISSION, withPackage = true) },
        ),
        PermRow(
            id = "accessibility",
            title = "UI Control",
            why = "Tap, scroll, type and navigate inside other apps — only on your command.",
            granted = AccessibilityController.isServiceEnabledInSettings(ctx) ||
                AccessibilityController.isConnected.value,
            actionLabel = "Open Settings",
            onAction = { openScreen(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        ),
        PermRow(
            id = "recognizer",
            title = "Speech engine",
            why = if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
                "On-device speech recognition is ready."
            } else {
                "No speech engine found — install the Google app or Gboard voice typing."
            },
            granted = SpeechRecognizer.isRecognitionAvailable(ctx),
            infoOnly = true,
        ),
    )
}
