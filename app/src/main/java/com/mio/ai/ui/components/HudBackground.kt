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
import com.mio.ai.ui.theme.mioFx

/**
 * Quiet cinematic backdrop: near-black gradient, a whisper of cyan aura
 * up top, soft vignette. No grid, no scanlines — restraint is the identity.
 */
@Composable
fun HudBackground(modifier: Modifier = Modifier) {
    val mio = mioColors
    val fx = mioFx
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(listOf(mio.void, mio.base, mio.void)),
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        mio.accent.copy(alpha = 0.055f * fx.accentIntensity),
                        Color.Transparent,
                    ),
                    center = Offset(size.width / 2f, -size.height * 0.08f),
                    radius = size.width * 0.95f,
                ),
            )
            // Gentle vignette for depth.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = maxOf(size.width, size.height) * 0.75f,
                ),
            )
        }
    }
}
