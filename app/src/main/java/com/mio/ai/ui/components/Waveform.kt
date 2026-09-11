package com.mio.ai.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.vm.AssistantStatus
import kotlin.math.sin

/**
 * Voice waveform: driven by the live mic level while listening, gently
 * animated while speaking, near-flat idle shimmer otherwise.
 */
@Composable
fun Waveform(
    level: Float,
    status: AssistantStatus,
    reduceMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val mio = mioColors
    val infinite = rememberInfiniteTransition(label = "wave")
    val phase by infinite.animateFloat(
        initialValue = 0f, targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "phase",
    )
    val glow = statusColor(status)
    val bars = 30

    Canvas(modifier.fillMaxWidth().height(44.dp)) {
        val gap = size.width / (bars * 2f)
        val barW = gap * 0.9f
        for (i in 0 until bars) {
            val wave = sin(phase * 1.4f + i * 0.62f) * 0.5f + 0.5f
            val frac = when {
                reduceMotion -> 0.14f
                status == AssistantStatus.LISTENING ->
                    (0.12f + level.coerceIn(0f, 1f) * (0.55f + 0.45f * wave)).coerceAtMost(1f)
                status == AssistantStatus.SPEAKING || status == AssistantStatus.EXECUTING ->
                    0.22f + 0.38f * wave
                status == AssistantStatus.THINKING -> 0.16f + 0.22f * wave
                else -> 0.07f + 0.05f * wave
            }
            val h = (size.height * frac).coerceAtLeast(3f)
            val x = gap * 0.5f + i * gap * 2f
            drawRoundRect(
                color = if (status == AssistantStatus.IDLE) mio.textMuted.copy(alpha = 0.5f) else glow,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}
