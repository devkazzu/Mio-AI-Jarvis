package com.mio.ai.ui.overlay

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mio.ai.assistant.AssistantCore
import com.mio.ai.ui.components.GlassSurface
import com.mio.ai.ui.components.MioOrb
import com.mio.ai.ui.components.PanelAccent
import com.mio.ai.ui.components.PrimaryButton
import com.mio.ai.ui.components.SecondaryButton
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTheme
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.theme.rememberEffectiveMotion
import com.mio.ai.ui.theme.themeModeOf
import com.mio.ai.ui.vm.AssistantStatus
import com.mio.ai.ui.vm.EntryKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

/**
 * Floating orb window content: the same Mio orb as the main app, small.
 * Tap opens the voice panel; drag moves the icon (a long-press pulses the
 * orb and buzzes so the move gesture is discoverable).
 */
@Composable
fun FloatingOrbContent(
    core: AssistantCore,
    movePulse: StateFlow<Int>,
    onTap: () -> Unit,
    onMoveStart: () -> Unit,
    onMove: (dxPx: Float, dyPx: Float) -> Unit,
    onMoveEnd: () -> Unit,
) {
    val settings by core.settings.collectAsState()
    val status by core.status.collectAsState()
    val rms by core.rms.collectAsState()
    val pulse by movePulse.collectAsState()
    MioTheme(
        mode = themeModeOf(settings.theme),
        accentIntensity = settings.accentIntensity,
        motion = rememberEffectiveMotion(settings.animation),
    ) {
        var pulsing by remember { mutableStateOf(false) }
        LaunchedEffect(pulse) {
            if (pulse > 0) {
                pulsing = true
                delay(700)
                pulsing = false
            }
        }
        val scale by animateFloatAsState(
            if (pulsing) 1.14f else 1f,
            animationSpec = tween(180),
            label = "grab",
        )
        val view = LocalView.current
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer(scaleX = scale, scaleY = scale)
                .orbGestures(
                    onTap = onTap,
                    onLongPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onMoveStart()
                    },
                    onMove = onMove,
                    onMoveEnd = onMoveEnd,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // The orb's own tap target is a no-op: the parent owns gestures.
            MioOrb(status = status, amplitude01 = rms, onTap = {}, size = 64.dp)
        }
    }
}

/**
 * Tap vs drag vs long-press for the orb. Tap = release without crossing
 * touch slop; drag moves immediately; holding still past the long-press
 * timeout fires [onLongPress] (then drag-or-release, never a tap).
 */
private fun Modifier.orbGestures(
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onMove: (dxPx: Float, dyPx: Float) -> Unit,
    onMoveEnd: () -> Unit,
): Modifier = pointerInput(onTap, onLongPress, onMove, onMoveEnd) {
    val slop = viewConfiguration.touchSlop
    val longMs = viewConfiguration.longPressTimeoutMillis
    awaitEachGesture {
        val down: PointerInputChange = awaitFirstDown(requireUnconsumed = false)
        val downAt = SystemClock.uptimeMillis()
        val downPos = down.position
        var moved = false
        var longFired = false
        var done = false
        while (!done) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) {
                if (!moved && !longFired) onTap()
                if (moved || longFired) onMoveEnd()
                done = true
            } else if (!moved && (change.position - downPos).getDistance() > slop) {
                moved = true
            } else if (moved) {
                val d = change.positionChange()
                if (d.x != 0f || d.y != 0f) {
                    onMove(d.x, d.y)
                    change.consume()
                }
            } else if (!longFired && SystemClock.uptimeMillis() - downAt >= longMs) {
                longFired = true
                onLongPress()
            }
        }
    }
}

/**
 * Compact voice panel: mini orb, live status, mic + cancel, the latest
 * reply/action line, and confirmation buttons when a plan awaits approval.
 * No text input (overlay windows stay non-focusable on purpose).
 */
@Composable
fun VoicePanelContent(
    core: AssistantCore,
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenPermissions: (highlight: String?) -> Unit,
) {
    val settings by core.settings.collectAsState()
    val status by core.status.collectAsState()
    val partial by core.partial.collectAsState()
    val rms by core.rms.collectAsState()
    val ticker by core.ticker.collectAsState()
    val entries by core.entries.collectAsState()
    val pending by core.pendingPlan.collectAsState()
    MioTheme(
        mode = themeModeOf(settings.theme),
        accentIntensity = settings.accentIntensity,
        motion = rememberEffectiveMotion(settings.animation),
    ) {
        val mio = mioColors
        val dim = mioDimens
        val lastLine = entries.lastOrNull { it.kind != EntryKind.USER }?.text
            ?: "Tap Talk and tell me what to do."
        val accent = when {
            status == AssistantStatus.ERROR -> PanelAccent.DANGER
            pending != null -> PanelAccent.WARNING
            status == AssistantStatus.LISTENING -> PanelAccent.CYAN
            else -> null
        }
        Box(Modifier.width(320.dp)) {
            GlassSurface(modifier = Modifier.fillMaxWidth(), accent = accent) {
                Column(verticalArrangement = Arrangement.spacedBy(dim.sm)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dim.md),
                    ) {
                        MioOrb(status = status, amplitude01 = rms, onTap = {}, size = 64.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                statusLabel(status).uppercase(),
                                style = CaptionMono,
                                color = mio.accent,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                when {
                                    status == AssistantStatus.LISTENING && partial.isNotBlank() -> "“$partial”"
                                    status == AssistantStatus.LISTENING -> "Listening — speak now"
                                    status == AssistantStatus.EXECUTING && ticker != null -> ticker!!
                                    status == AssistantStatus.EXECUTING -> "Working…"
                                    status == AssistantStatus.THINKING -> "Understanding…"
                                    status == AssistantStatus.SPEAKING -> "Replying…"
                                    status == AssistantStatus.ERROR -> "Tap Fix or try again"
                                    else -> "Idle"
                                },
                                style = MioTypography.bodyMedium,
                                color = mio.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Text(
                        lastLine,
                        style = MioTypography.bodyMedium,
                        color = mio.textSecondary,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (pending != null) {
                        Text(
                            pending!!.confirmationPrompt ?: "Go ahead?",
                            style = MioTypography.bodyLarge,
                            color = mio.textPrimary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(dim.sm)) {
                            PrimaryButton(
                                "Yes", core::confirmPending,
                                Modifier.weight(1f), compact = true,
                            )
                            SecondaryButton(
                                "No", core::dismissPending,
                                Modifier.weight(1f), compact = true,
                            )
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(dim.sm)) {
                            PrimaryButton(
                                if (status == AssistantStatus.LISTENING) "Stop" else "Talk",
                                core::onMicPress,
                                Modifier.weight(1f),
                                compact = true,
                                icon = if (status == AssistantStatus.LISTENING) Icons.Filled.MicOff else Icons.Filled.Mic,
                            )
                            SecondaryButton(
                                "Cancel",
                                {
                                    when (core.status.value) {
                                        AssistantStatus.LISTENING,
                                        AssistantStatus.SPEAKING,
                                        AssistantStatus.THINKING -> core.onMicPress()
                                        AssistantStatus.EXECUTING -> core.stopExecution()
                                        else -> Unit
                                    }
                                    onClose()
                                },
                                Modifier.weight(1f),
                                compact = true,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = onOpenApp) {
                            Text("OPEN MIO", style = CaptionMono, color = mio.accent)
                        }
                        if (status == AssistantStatus.ERROR) {
                            TextButton(onClick = { onOpenPermissions("mic") }) {
                                Text("FIX", style = CaptionMono, color = mio.warning)
                            }
                        }
                    }
                }
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(48.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = mio.textSecondary)
            }
        }
    }
}

private fun statusLabel(status: AssistantStatus): String = when (status) {
    AssistantStatus.IDLE -> "Idle"
    AssistantStatus.LISTENING -> "Listening"
    AssistantStatus.THINKING -> "Thinking"
    AssistantStatus.SPEAKING -> "Speaking"
    AssistantStatus.EXECUTING -> "Working"
    AssistantStatus.ERROR -> "Attention needed"
}
