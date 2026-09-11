package com.mio.ai.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.mio.ai.ui.vm.AssistantViewModel

/**
 * "Display over other apps" setup for the floating Mio orb.
 *
 * Plain-language why, one checklist, one primary button that walks the
 * chain (microphone → overlay → start). Nothing is requested twice and
 * nothing starts until the user taps the final button. If overlay access
 * is denied, the manual path is shown instead of nagging.
 */
@Composable
fun OverlayPermissionScreen(vm: AssistantViewModel, onBack: () -> Unit, onDone: () -> Unit) {
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

    val micOk = remember(tick) { hasOverlayPerm(ctx, Manifest.permission.RECORD_AUDIO) }
    val overlayOk = remember(tick) { Settings.canDrawOverlays(ctx) }
    val notifNeeded = Build.VERSION.SDK_INT >= 33
    val notifOk = remember(tick) {
        !notifNeeded || hasOverlayPerm(ctx, Manifest.permission.POST_NOTIFICATIONS)
    }

    val (primaryLabel, primaryAction) = when {
        !micOk -> "Continue" to { runtimeLauncher.launch(Manifest.permission.RECORD_AUDIO) }
        !overlayOk -> "Enable Floating Mio" to { openOverlaySettings(ctx) }
        else -> "Start background assistant" to { vm.setBackgroundAssistant(true); onDone() }
    }

    Box(Modifier.fillMaxSize()) {
        HudBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = dim.gutter),
        ) {
            MioTopBar(title = "Floating Mio", onBack = onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dim.md),
            ) {
                GlassSurface(accent = if (overlayOk) PanelAccent.SUCCESS else PanelAccent.CYAN) {
                    Column {
                        Text(
                            if (overlayOk) "FLOATING MIO IS READY" else "WHY MIO NEEDS THIS",
                            style = CaptionMono,
                            color = if (overlayOk) mio.success else mio.accent,
                        )
                        Spacer(Modifier.height(dim.sm))
                        Text(
                            "Background assistant keeps Mio one tap away while you use other " +
                                "apps: a small orb floats above them, and tapping it opens a " +
                                "compact voice panel — no need to return to this app.",
                            style = MioTypography.bodyLarge,
                            color = mio.textPrimary,
                        )
                        Spacer(Modifier.height(dim.sm))
                        Text(
                            "Android requires “Display over other apps” for the floating icon. " +
                                "Mio only listens after you tap, shows a persistent notification " +
                                "while running, and stops the moment you ask.",
                            style = MioTypography.bodyMedium,
                            color = mio.textSecondary,
                        )
                    }
                }
                SectionHeader(title = "Setup")
                PermissionCard(
                    title = "Microphone",
                    why = "Hears your commands when you tap the orb.",
                    granted = micOk,
                    onCta = { runtimeLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                )
                PermissionCard(
                    title = "Display over other apps",
                    why = "Draws the floating Mio icon above other apps.",
                    granted = overlayOk,
                    ctaLabel = "Open Settings",
                    onCta = { openOverlaySettings(ctx); tick++ },
                )
                PermissionCard(
                    title = "Notifications",
                    why = if (notifNeeded) {
                        "Shows the background assistant while it runs. Recommended."
                    } else {
                        "Not required below Android 13."
                    },
                    granted = notifOk,
                    infoOnly = !notifNeeded,
                    onCta = if (notifNeeded && !notifOk) {
                        { runtimeLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                    } else {
                        null
                    },
                )
                PrimaryButton(primaryLabel, primaryAction, Modifier.fillMaxWidth())
                if (!overlayOk) {
                    InfoNote(
                        "If you skipped the system toggle: Settings → Apps → Mio AI → " +
                            "“Display over other apps”, then come back and tap “Enable Floating Mio”.",
                    )
                }
                InfoNote(
                    "Stop anytime: notification → Stop, or Settings → Background assistant → off. " +
                        "The orb and the notification disappear together.",
                )
                MioDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = dim.sm))
                Spacer(Modifier.height(dim.xl))
            }
        }
    }
}

private fun hasOverlayPerm(ctx: Context, perm: String): Boolean =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

private fun openOverlaySettings(ctx: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${ctx.packageName}"),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(intent) }
}
