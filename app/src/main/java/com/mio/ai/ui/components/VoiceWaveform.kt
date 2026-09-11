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
import com.mio.ai.ui.theme.MioMotion
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioMotion
import com.mio.ai.ui.vm.AssistantStatus
import kotlin.math.sin

/**
 * Compact voice waveform: mic-driven while listening, gently alive while
 * speaking, near-flat idle shimmer. Static when motion is OFF.
 */
@Composable
fun VoiceWaveform(
    level: Float,
    status: AssistantStatus,
    modifier: Modifier = Modifier,
    motion: MioMotion = mioMotion,
) {
    val mio = mioColors
    val infinite = rememberInfiniteTransition(label = "wave")
    val phase by infinite.animateFloat(
        initialValue = 0f, targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "phase",
    )
    val glow = statusColor(status)
    val bars = 28

    Canvas(modifier.fillMaxWidth().height(40.dp)) {
        val gap = size.width / (bars * 2f)
        val barW = gap * 0.85f
        for (i in 0 until bars) {
            val wave = sin(phase * 1.3f + i * 0.65f) * 0.5f + 0.5f
            val frac = when {
                !motion.waveform -> 0.12f
                status == AssistantStatus.LISTENING ->
                    (0.10f + level.coerceIn(0f, 1f) * (0.55f + 0.45f * wave)).coerceAtMost(1f)
                status == AssistantStatus.SPEAKING -> 0.20f + 0.34f * wave
                status == AssistantStatus.THINKING || status == AssistantStatus.EXECUTING ->
                    0.14f + 0.20f * wave
                else -> 0.06f + 0.04f * wave
            }
            val h = (size.height * frac).coerceAtLeast(3f)
            val x = gap * 0.5f + i * gap * 2f
            drawRoundRect(
                color = if (status == AssistantStatus.IDLE) mio.textMuted.copy(alpha = 0.45f) else glow,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barW, h),
                cornerRadius = CornerRadius(barW / 2f, barW / 2f),
            )
        }
    }
}
