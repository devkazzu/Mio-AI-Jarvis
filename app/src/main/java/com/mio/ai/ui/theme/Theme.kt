package com.mio.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * MioTheme: dark-only HUD theme bound to [MioColors].
 *
 * Optional 1:1 design-system typography via downloadable Google Fonts —
 * add `androidx.compose.ui:ui-text-google-fonts`, then:
 *
 * ```
 * val provider = GoogleFont.Provider(
 *     "com.google.android.gms.fonts",
 *     "com.google.android.gms",
 *     certificates = R.array.com_google_android_gms_fonts_certs)
 * val syncopate = FontFamily(Font(GoogleFont("Syncopate"), provider))
 * ```
 * and point [DisplayFamily] at it. The system-family defaults look close and
 * work fully offline, so this stays optional.
 */
@Composable
fun MioTheme(content: @Composable () -> Unit) {
    val mio = MioColors()
    val scheme = darkColorScheme(
        primary = mio.primary,
        onPrimary = Color(0xFF04222A),
        secondary = mio.secondary,
        onSecondary = Color.White,
        tertiary = mio.executing,
        background = mio.void,
        onBackground = mio.textPrimary,
        surface = mio.abyss,
        onSurface = mio.textPrimary,
        surfaceVariant = mio.glass,
        onSurfaceVariant = mio.textMuted,
        error = mio.danger,
        onError = Color(0xFF2A0A0A),
        outline = mio.hudLine,
    )
    CompositionLocalProvider(LocalMioColors provides mio) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MioTypography,
            content = content,
        )
    }
}

/** Shorthand for components: `val mio = mioColors()`. */
val mioColors: MioColors
    @Composable
    get() = LocalMioColors.current
