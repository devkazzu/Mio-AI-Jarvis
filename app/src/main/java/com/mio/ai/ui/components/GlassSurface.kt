package com.mio.ai.ui.components

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.theme.mioFx

/**
 * The one Mio container: quiet surface + 1px line. Corner brackets appear
 * ONLY when [accent] is set — reserved for important interactive panels
 * (live execution, pending confirmation, permission hero).
 */
enum class PanelAccent { CYAN, SUCCESS, WARNING, DANGER }

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    accent: PanelAccent? = null,
    radius: Dp? = null,
    content: @Composable () -> Unit,
) {
    val mio = mioColors
    val dim = mioDimens
    val fx = mioFx
    val r = radius ?: dim.radiusLg
    val accentColor = when (accent) {
        PanelAccent.CYAN -> mio.accent
        PanelAccent.SUCCESS -> mio.success
        PanelAccent.WARNING -> mio.warning
        PanelAccent.DANGER -> mio.danger
        null -> Color.Transparent
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(r),
        color = mio.surface,
        border = BorderStroke(1.dp, if (accent != null) accentColor.copy(alpha = 0.35f * fx.accentIntensity) else mio.line),
    ) {
        Box {
            Box(Modifier.padding(dim.lg)) { content() }
            if (accent != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val len = 22f
                    val w = 2.5f
                    val glow = accentColor.copy(alpha = 0.9f * fx.accentIntensity)
                    val maxX = size.width
                    val maxY = size.height
                    val m = 7f
                    fun bracket(x0: Float, y0: Float, dx: Float, dy: Float) {
                        drawLine(glow, Offset(x0, y0), Offset(x0 + dx * len, y0), w)
                        drawLine(glow, Offset(x0, y0), Offset(x0, y0 + dy * len), w)
                    }
                    bracket(m, m, 1f, 1f)
                    bracket(maxX - m, m, -1f, 1f)
                    bracket(m, maxY - m, 1f, -1f)
                    bracket(maxX - m, maxY - m, -1f, -1f)
                }
            }
        }
    }
}

/** Hairline divider. */
@Composable
fun MioDivider(modifier: Modifier = Modifier) {
    val mio = mioColors
    Canvas(modifier) {
        drawLine(
            color = mio.line,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1f,
        )
    }
}
