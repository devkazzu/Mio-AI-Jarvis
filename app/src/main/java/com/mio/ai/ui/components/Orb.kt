package com.mio.ai.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.vm.AssistantStatus
import kotlin.math.cos
import kotlin.math.sin

/** Status → HUD glow color (matches MASTER.md). */
@Composable
fun statusColor(status: AssistantStatus): Color {
    val mio = mioColors
    return when (status) {
        AssistantStatus.IDLE -> mio.textMuted
        AssistantStatus.LISTENING -> mio.primary
        AssistantStatus.THINKING -> mio.secondary
        AssistantStatus.SPEAKING -> mio.speaking
        AssistantStatus.EXECUTING -> mio.executing
        AssistantStatus.ERROR -> mio.danger
    }
}

/**
 * Animated central AI orb: glowing core, counter-rotating dashed rings and
 * orbiting particles. Reacts to [AssistantStatus] + live mic [level].
 * Fully static when [reduceMotion] is on (accessibility).
 */
@Composable
fun MioOrb(
    status: AssistantStatus,
    level: Float,
    reduceMotion: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 228.dp,
) {
    val mio = mioColors
    val glow = statusColor(status)

    val infinite = rememberInfiniteTransition(label = "orb")
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing)),
        label = "rotation",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.94f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val rot = if (reduceMotion) 24f else rotation
    val scl = if (reduceMotion) 1f else pulse
    val energy = level.coerceIn(0f, 1f)

    Canvas(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Talk to Mio") { onTap() },
    ) {
        val d = size.minDimension
        val c = Offset(d / 2f, d / 2f)

        // Outer aura.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glow.copy(alpha = 0.30f), Color.Transparent),
                center = c,
                radius = d / 2f,
            ),
            radius = d / 2f,
            center = c,
        )

        // Counter-rotating dashed rings.
        val rings = listOf(
            Triple(0.98f, 3f, 26f), // (diameter fraction, stroke, dash)
            Triple(0.88f, 2f, 12f),
            Triple(0.78f, 5f, 46f),
        )
        rings.forEachIndexed { i, (frac, stroke, dash) ->
            val rd = d * frac
            rotate((if (i % 2 == 0) rot else -rot * 1.4f) + i * 53f, c) {
                drawArc(
                    color = glow.copy(alpha = 0.55f - i * 0.1f),
                    startAngle = 0f,
                    sweepAngle = 292f - i * 34f,
                    useCenter = false,
                    topLeft = Offset(c.x - rd / 2f, c.y - rd / 2f),
                    size = Size(rd, rd),
                    style = dashedStroke(stroke, dash, dash * 0.55f),
                )
            }
        }

        // Listening energy ring (mic-reactive).
        if (status == AssistantStatus.LISTENING && energy > 0.02f && !reduceMotion) {
            val rd = d * (0.70f + energy * 0.10f)
            drawArc(
                color = mio.primary.copy(alpha = 0.35f + energy * 0.5f),
                startAngle = -90f,
                sweepAngle = 120f + energy * 220f,
                useCenter = false,
                topLeft = Offset(c.x - rd / 2f, c.y - rd / 2f),
                size = Size(rd, rd),
                style = Stroke(width = 6f),
            )
        }

        // Core.
        val coreR = d * 0.30f * scl * (1f + energy * 0.12f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, glow, mio.secondary.copy(alpha = 0.85f), Color.Transparent),
                center = c,
                radius = coreR * 1.9f,
            ),
            radius = coreR * 1.9f,
            center = c,
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.95f), glow.copy(alpha = 0.55f)),
                center = c,
                radius = coreR,
            ),
            radius = coreR,
            center = c,
        )

        // Orbiting particles.
        if (!reduceMotion) {
            for (k in 0 until 3) {
                val a = Math.toRadians((rot * 1.6 + k * 120f).toDouble())
                val orbitR = d * 0.44f
                val p = Offset(
                    c.x + (orbitR * cos(a)).toFloat(),
                    c.y + (orbitR * sin(a)).toFloat(),
                )
                drawCircle(glow.copy(alpha = 0.25f), radius = 10f, center = p)
                drawCircle(Color.White, radius = 3.2f, center = p)
            }
        }
    }
}
