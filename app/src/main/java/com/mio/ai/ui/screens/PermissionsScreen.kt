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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.mio.ai.accessibility.AccessibilityController
import com.mio.ai.ui.components.HudBackground
import com.mio.ai.ui.theme.MonoLabel
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors

private data class PermRow(
    val id: String,
    val title: String,
    val why: String,
    val granted: Boolean,
    val infoOnly: Boolean = false,
    val actionLabel: String = "ALLOW",
    val onAction: (() -> Unit)? = null,
)

/**
 * Every capability Mio can use, why it needs it, and a one-tap path to
 * grant it. [highlight] (from failed actions) spotlights the needed row.
 */
@Composable
fun PermissionsScreen(highlight: String?, onBack: () -> Unit) {
    val mio = mioColors
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
    val grantedCount = rows.count { it.granted || it.infoOnly }

    Box(Modifier.fillMaxSize()) {
        HudBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = mio.textPrimary)
                }
                Column(Modifier.weight(1f)) {
                    Text("PERMISSIONS", style = MioTypography.titleMedium, color = mio.textPrimary)
                    Text(
                        "$grantedCount / ${rows.size} READY",
                        style = MonoLabel,
                        color = mio.textMuted,
                    )
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        "Mio only uses what your commands need. Grant access as features ask for it — " +
                            "everything keeps working except the feature tied to a missing permission.",
                        color = mio.textMuted,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    )
                }
                items(rows, key = { it.id }) { row ->
                    PermCard(row = row, highlighted = row.id == highlight)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun PermCard(row: PermRow, highlighted: Boolean) {
    val mio = mioColors
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = mio.glass,
        border = BorderStroke(
            1.dp,
            when {
                highlighted && !row.granted -> mio.warning
                row.granted -> mio.hudLine
                else -> mio.danger.copy(alpha = 0.5f)
            },
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    row.infoOnly -> Icons.Filled.Info
                    row.granted -> Icons.Filled.Check
                    else -> Icons.Filled.Close
                },
                contentDescription = null,
                tint = when {
                    row.infoOnly -> mio.textMuted
                    row.granted -> mio.success
                    else -> mio.danger
                },
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.title, color = mio.textPrimary, fontSize = 14.sp)
                    if (highlighted && !row.granted) {
                        Spacer(Modifier.width(8.dp))
                        Text("NEEDED NOW", style = MonoLabel, color = mio.warning)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(row.why, color = mio.textMuted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            if (!row.granted && !row.infoOnly && row.onAction != null) {
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = row.onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = mio.primary,
                        contentColor = Color(0xFF04222A),
                    ),
                ) { Text(row.actionLabel, style = MonoLabel) }
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
            why = "Voice commands and the optional wake word.",
            granted = hasPerm(ctx, Manifest.permission.RECORD_AUDIO),
            onAction = { request(Manifest.permission.RECORD_AUDIO) },
        ),
        PermRow(
            id = "notifications",
            title = "Notifications",
            why = if (notifNeeded) "Shows the wake-word listener status." else "Not required below Android 13.",
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
            title = "Camera (flashlight)",
            why = "Only used to switch the torch on/off. Never photos.",
            granted = hasPerm(ctx, Manifest.permission.CAMERA),
            onAction = { request(Manifest.permission.CAMERA) },
        ),
        PermRow(
            id = "bluetooth",
            title = "Bluetooth",
            why = if (btNeeded) "Voice toggles for Bluetooth on/off." else "Included on this Android version.",
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
            why = "Resolve “call mom” to a phone number.",
            granted = hasPerm(ctx, Manifest.permission.READ_CONTACTS),
            onAction = { request(Manifest.permission.READ_CONTACTS) },
        ),
        PermRow(
            id = "write_settings",
            title = "Modify system settings",
            why = "Screen brightness control. Granted in system Settings.",
            granted = Settings.System.canWrite(ctx),
            actionLabel = "OPEN",
            onAction = { openScreen(ctx, Settings.ACTION_MANAGE_WRITE_SETTINGS, withPackage = true) },
        ),
        PermRow(
            id = "dnd",
            title = "Do Not Disturb access",
            why = "Silence/unsilence the phone by voice. Granted in system Settings.",
            granted = ctx.getSystemService(NotificationManager::class.java)
                ?.isNotificationPolicyAccessGranted == true,
            actionLabel = "OPEN",
            onAction = { openScreen(ctx, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) },
        ),
        PermRow(
            id = "accessibility",
            title = "UI Control (accessibility)",
            why = "Tap, scroll, type and navigate inside other apps — only when you command it.",
            granted = AccessibilityController.isServiceEnabledInSettings(ctx) ||
                AccessibilityController.isConnected.value,
            actionLabel = "OPEN",
            onAction = { openScreen(ctx, Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        ),
        PermRow(
            id = "recognizer",
            title = "Speech recognizer",
            why = if (SpeechRecognizer.isRecognitionAvailable(ctx)) {
                "On-device speech engine is ready."
            } else {
                "No speech engine found — install Google app / Gboard voice typing."
            },
            granted = SpeechRecognizer.isRecognitionAvailable(ctx),
            infoOnly = true,
        ),
    )
}
