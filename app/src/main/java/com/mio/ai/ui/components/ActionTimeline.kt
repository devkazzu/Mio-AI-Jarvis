package com.mio.ai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.theme.mioMotion
import com.mio.ai.ui.vm.StepUi
import com.mio.ai.ui.vm.StepVisual

/**
 * Vertical execution rail: pending → running → done/fail, updating live.
 * Compact technical style per spec ("MIO IS WORKING" panel body).
 */
@Composable
fun ActionTimeline(
    steps: List<StepUi>,
    modifier: Modifier = Modifier,
) {
    val mio = mioColors
    val dim = mioDimens
    Column(modifier = modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.Top) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(22.dp),
                ) {
                    StepNode(step.state)
                    if (index != steps.lastIndex) {
                        Canvas(Modifier.size(width = 2.dp, height = 14.dp)) {
                            drawRect(
                                color = if (step.state == StepVisual.DONE_OK) mio.success.copy(alpha = 0.5f)
                                else mio.line,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(dim.sm))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = if (index != steps.lastIndex) dim.sm else 0.dp),
                ) {
                    Text(
                        step.label,
                        style = MioTypography.bodyMedium,
                        color = if (step.state == StepVisual.PENDING) mio.textMuted else mio.textPrimary,
                    )
                    if (step.detail != null && step.state != StepVisual.PENDING) {
                        Text(step.detail, style = CaptionMono, color = mio.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepNode(state: StepVisual) {
    val mio = mioColors
    val motion = mioMotion
    when (state) {
        StepVisual.PENDING -> {
            Canvas(Modifier.size(18.dp)) {
                drawCircle(
                    color = mio.textMuted.copy(alpha = 0.5f),
                    radius = 5f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f),
                )
            }
        }
        StepVisual.RUNNING -> {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = mio.accent,
            )
        }
        StepVisual.DONE_OK -> {
            // Subtle success pop.
            val scale by animateFloatAsState(
                if (motion.transitions) 1f else 1f,
                animationSpec = tween(mioDimens.durationNormal),
                label = "checkPop",
            )
            Icon(
                Icons.Filled.Check,
                contentDescription = "Done",
                tint = mio.success,
                modifier = Modifier
                    .size(18.dp)
                    .scale(scale),
            )
        }
        StepVisual.DONE_FAIL -> {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Failed",
                tint = mio.danger,
                modifier = Modifier.size(18.dp),
            )
        }
        StepVisual.CANCELLED -> {
            Canvas(Modifier.size(18.dp)) {
                drawCircle(color = mio.textMuted.copy(alpha = 0.6f), radius = 4f)
            }
        }
    }
}

/** One-line horizontal step summary (compact rows in the Actions log). */
@Composable
fun StepSummaryLine(ok: Int, total: Int, failed: Boolean, cancelled: Boolean) {
    val mio = mioColors
    val text = when {
        cancelled -> "Stopped · $ok/$total steps"
        failed -> "$ok/$total steps · failed"
        else -> "$ok/$total steps · done"
    }
    val color = when {
        cancelled -> mio.textMuted
        failed -> mio.danger
        else -> mio.success
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(mioDimens.xs),
    ) {
        Canvas(Modifier.size(mioDimens.xs)) { drawCircle(color) }
        Text(text, style = CaptionMono, color = color)
    }
}
