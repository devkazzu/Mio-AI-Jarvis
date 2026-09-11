package com.mio.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.mioColors

/**
 * Glass panel with a 1px HUD border and glowing corner brackets —
 * the signature Mio container.
 */
@Composable
fun HudPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 16.dp,
    bracketColor: Color = Color.Unspecified,
    content: @Composable () -> Unit,
) {
    val mio = mioColors
    val accent = if (bracketColor == Color.Unspecified) mio.primary else bracketColor
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius),
        color = mio.glass,
        border = androidx.compose.foundation.BorderStroke(1.dp, mio.hudLine),
    ) {
        Box {
            Box(Modifier.padding(14.dp)) { content() }
            Canvas(Modifier.fillMaxSize()) {
                val len = 26f
                val w = 3f
                val r = cornerRadius.toPx()
                val maxX = size.width
                val maxY = size.height
                fun bracket(x0: Float, y0: Float, dx: Float, dy: Float) {
                    drawLine(accent, Offset(x0, y0), Offset(x0 + dx * len, y0), w)
                    drawLine(accent, Offset(x0, y0), Offset(x0, y0 + dy * len), w)
                }
                bracket(r * 0.4f, r * 0.4f, 1f, 1f)
                bracket(maxX - r * 0.4f, r * 0.4f, -1f, 1f)
                bracket(r * 0.4f, maxY - r * 0.4f, 1f, -1f)
                bracket(maxX - r * 0.4f, maxY - r * 0.4f, -1f, -1f)
            }
        }
    }
}

/** Thin dashed divider used between HUD sections. */
@Composable
fun HudDivider(modifier: Modifier = Modifier) {
    val mio = mioColors
    Canvas(modifier) {
        drawLine(
            color = mio.hudLine,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
        )
    }
}

/** Dashed arc ring helper shared by the orb. */
internal fun dashedStroke(width: Float, dash: Float, gap: Float) =
    Stroke(width = width, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, gap)))
