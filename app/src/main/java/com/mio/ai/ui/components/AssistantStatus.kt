package com.mio.ai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.theme.mioMotion
import com.mio.ai.ui.vm.AssistantStatus

/** Status → color (single-accent discipline; violet only for thinking). */
@Composable
fun statusColor(status: AssistantStatus): Color {
    val mio = mioColors
    return when (status) {
        AssistantStatus.IDLE -> mio.textMuted
        AssistantStatus.LISTENING -> mio.accent
        AssistantStatus.THINKING -> mio.violet
        AssistantStatus.SPEAKING -> mio.accent
        AssistantStatus.EXECUTING -> mio.accent
        AssistantStatus.ERROR -> mio.danger
    }
}

/** Status → calm human label. */
fun statusLabel(status: AssistantStatus): String = when (status) {
    AssistantStatus.IDLE -> "Ready"
    AssistantStatus.LISTENING -> "Listening…"
    AssistantStatus.THINKING -> "Thinking…"
    AssistantStatus.SPEAKING -> "Speaking…"
    AssistantStatus.EXECUTING -> "Executing…"
    AssistantStatus.ERROR -> "Attention needed"
}

/**
 * Status pill: breathing dot + label + optional detail line.
 * Compact in the top bar, centered under the orb.
 */
@Composable
fun AssistantStatusView(
    status: AssistantStatus,
    modifier: Modifier = Modifier,
    detail: String? = null,
    centered: Boolean = false,
) {
    val mio = mioColors
    val dim = mioDimens
    val motion = mioMotion
    val color = statusColor(status)

    val infinite = rememberInfiniteTransition(label = "statusDot")
    val alpha by infinite.animateFloat(
        initialValue = 0.45f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dotPulse",
    )
    val active = status == AssistantStatus.LISTENING ||
        status == AssistantStatus.THINKING ||
        status == AssistantStatus.EXECUTING
    val dotAlpha = if (motion.pulse && active) alpha else 1f

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
    ) {
        Canvas(Modifier.size(dim.xs)) {
            drawCircle(color = color.copy(alpha = dotAlpha * 0.25f), radius = size.minDimension / 2f * 1.7f)
            drawCircle(color = color.copy(alpha = dotAlpha), radius = size.minDimension / 2f)
        }
        Spacer(Modifier.width(dim.sm))
        Column {
            Text(statusLabel(status).uppercase(), style = CaptionMono, color = color)
            if (detail != null) {
                Text(detail, style = MioTypography.bodyMedium, color = mio.textSecondary)
            }
        }
    }
}
