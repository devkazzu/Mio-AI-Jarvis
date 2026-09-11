package com.mio.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.mio.ai.ui.theme.mioColors

/**
 * Full-screen HUD backdrop: deep-space gradient, faint technical grid,
 * scanlines and a violet vignette. Pure Canvas — no assets needed.
 */
@Composable
fun HudBackground(modifier: Modifier = Modifier) {
    val mio = mioColors
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // Base gradient.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(mio.void, mio.abyss, mio.void),
                ),
            )
            // Violet aura top-center.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        mio.secondary.copy(alpha = 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, -size.height * 0.05f),
                    radius = size.width * 0.9f,
                ),
            )
            // Technical grid.
            val step = 96f
            val gridColor = mio.primary.copy(alpha = 0.045f)
            var x = 0f
            while (x <= size.width) {
                drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(gridColor, Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
            // Scanlines.
            val scan = Color.White.copy(alpha = 0.012f)
            var sy = 0f
            while (sy <= size.height) {
                drawLine(scan, Offset(0f, sy), Offset(size.width, sy), 1f)
                sy += 5f
            }
        }
    }
}
