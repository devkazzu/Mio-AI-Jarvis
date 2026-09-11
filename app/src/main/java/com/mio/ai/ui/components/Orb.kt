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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.MioFx
import com.mio.ai.ui.theme.MioMotion
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.theme.mioFx
import com.mio.ai.ui.theme.mioMotion
import com.mio.ai.ui.vm.AssistantStatus
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Mio orb: calm, state-reactive, amplitude-aware.
 * Restrained by design — thin rings, soft aura, motion only where it informs.
 */
@Composable
fun MioOrb(
    status: AssistantStatus,
    amplitude01: Float,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 224.dp,
    motion: MioMotion = mioMotion,
    fx: MioFx = mioFx,
) {
    val mio = mioColors
    val dim = mioDimens
    val glow = statusColor(status)
    val energy = amplitude01.coerceIn(0f, 1f)
    val intensity = fx.accentIntensity

    val infinite = rememberInfiniteTransition(label = "orb")
    val rotation by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(dim.durationOrbit, easing = LinearEasing)),
        label = "rotation",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.97f, targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            tween(dim.durationPulse, easing = FastOutSlowInEasing), RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val rot = if (motion.rotation) rotation else 24f
    val breathe = if (motion.pulse) pulse else 1f

    Canvas(
        modifier
            .size(size)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = "Talk to Mio") { onTap() },
    ) {
        val d = size.minDimension
        val c = Offset(d / 2f, d / 2f)

        // Soft aura (scaled by accent intensity).
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    glow.copy(alpha = 0.16f * intensity + energy * 0.10f),
                    Color.Transparent,
                ),
                center = c,
                radius = d / 2f,
            ),
            radius = d / 2f,
            center = c,
        )

        // Thin static ring.
        run {
            val rd = d * 0.96f
            drawArc(
                color = glow.copy(alpha = 0.30f),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(c.x - rd / 2f, c.y - rd / 2f),
                size = Size(rd, rd),
                style = Stroke(width = 1.5f),
            )
        }

        // Dashed orbit ring (counter-rotates in FULL).
        run {
            val rd = d * 0.86f
            rotate(if (motion.rotation) -rot else 0f, c) {
                drawArc(
                    color = glow.copy(alpha = 0.45f),
                    startAngle = 0f, sweepAngle = 300f, useCenter = false,
                    topLeft = Offset(c.x - rd / 2f, c.y - rd / 2f),
                    size = Size(rd, rd),
                    style = Stroke(
                        width = 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                    ),
                )
            }
        }

        // State arc: listening energy / thinking & executing sweep.
        when (status) {
            AssistantStatus.LISTENING -> {
                val rd = d * (0.74f + energy * 0.06f)
                drawArc(
                    color = mio.accent.copy(alpha = (0.35f + energy * 0.55f) * intensity),
                    startAngle = -90f,
                    sweepAngle = 100f + energy * 240f,
                    useCenter = false,
                    topLeft = Offset(c.x - rd / 2f, c.y - rd / 2f),
                    size = Size(rd, rd),
                    style = Stroke(width = 4f),
                )
            }
            AssistantStatus.THINKING, AssistantStatus.EXECUTING -> {
                val rd = d * 0.74f
                rotate(if (motion.rotation) rot * 1.6f else 40f, c) {
                    drawArc(
                        color = glow.copy(alpha = 0.75f * intensity),
                        startAngle = 0f, sweepAngle = 95f, useCenter = false,
                        topLeft = Offset(c.x - rd / 2f, c.y - rd / 2f),
                        size = Size(rd, rd),
                        style = Stroke(width = 3.5f),
                    )
                }
            }
            else -> Unit
        }

        // Core — amplitude-reactive.
        val coreR = d * 0.27f * breathe * (1f + energy * 0.18f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f),
                    glow.copy(alpha = 0.75f),
                    glow.copy(alpha = 0.12f * intensity),
                ),
                center = c,
                radius = coreR * 1.35f,
            ),
            radius = coreR * 1.35f,
            center = c,
        )

        // Two quiet orbit particles (FULL only).
        if (motion.particles) {
            for (k in 0 until 2) {
                val a = Math.toRadians((rot * 1.4 + k * 180f).toDouble())
                val orbitR = d * 0.43f
                val p = Offset(
                    c.x + (orbitR * cos(a)).toFloat(),
                    c.y + (orbitR * sin(a)).toFloat(),
                )
                drawCircle(glow.copy(alpha = 0.20f * intensity), radius = 8f, center = p)
                drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.4f, center = p)
            }
        }
    }
}
