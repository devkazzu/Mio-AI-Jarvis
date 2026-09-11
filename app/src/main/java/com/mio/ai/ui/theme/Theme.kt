package com.mio.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * MioTheme v2: restrained dark theme bound to [MioColors]/[MioDimens]/
 * [MioMotion]/[MioFx]. Material 3 underneath, Mio identity on top.
 */
@Composable
fun MioTheme(
    mode: MioThemeMode,
    accentIntensity: Float,
    motion: MioMotion,
    content: @Composable () -> Unit,
) {
    val mio = remember(mode) {
        if (mode == MioThemeMode.ABYSS) MioColors.abyss() else MioColors.midnight()
    }
    val scheme = remember(mio) {
        darkColorScheme(
            primary = mio.accent,
            onPrimary = mio.void,
            secondary = mio.violet,
            onSecondary = Color.White,
            tertiary = mio.textSecondary,
            background = mio.void,
            onBackground = mio.textPrimary,
            surface = mio.surface,
            onSurface = mio.textPrimary,
            surfaceVariant = mio.surfaceHigh,
            onSurfaceVariant = mio.textSecondary,
            error = mio.danger,
            onError = Color(0xFF2A0A0A),
            outline = mio.line,
        )
    }
    CompositionLocalProvider(
        LocalMioColors provides mio,
        LocalMioDimens provides MioDimens(),
        LocalMioMotion provides motion,
        LocalMioFx provides MioFx(accentIntensity.coerceIn(0.3f, 1f)),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MioTypography,
            content = content,
        )
    }
}

/** Token shorthands for components. */
val mioColors: MioColors
    @Composable
    get() = LocalMioColors.current

val mioDimens: MioDimens
    @Composable
    get() = LocalMioDimens.current

val mioMotion: MioMotion
    @Composable
    get() = LocalMioMotion.current

val mioFx: MioFx
    @Composable
    get() = LocalMioFx.current
