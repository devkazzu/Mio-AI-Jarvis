package com.mio.ai.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centralized spacing / radius / elevation / duration tokens.
 * No magic numbers in components — use [mioDimens].
 */
data class MioDimens(
    // Spacing scale.
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
    // Radius scale.
    val radiusSm: Dp = 8.dp,
    val radiusMd: Dp = 12.dp,
    val radiusLg: Dp = 16.dp,
    val radiusXl: Dp = 24.dp,
    val radiusBubble: Dp = 18.dp,
    val radiusTail: Dp = 4.dp,
    // Layout.
    val gutter: Dp = 16.dp,
    val touchMin: Dp = 48.dp,
    // Motion durations (ms).
    val durationFast: Int = 120,
    val durationNormal: Int = 220,
    val durationSlow: Int = 350,
    val durationPulse: Int = 1800,
    val durationOrbit: Int = 16000,
)

val LocalMioDimens = staticCompositionLocalOf { MioDimens() }
