package com.mio.ai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Skill pairing "Kinetic Motion": wide display + mono micro-labels.
 * Implemented on system families with tracking so the app builds and runs
 * offline; swap [DisplayFamily]/[MonoFamily] for downloadable Google Fonts
 * (Syncopate / Space Mono) to match the design system 1:1 — see commented
 * snippet in MioTheme.kt.
 */
val DisplayFamily = FontFamily.SansSerif
val MonoFamily = FontFamily.Monospace

val MioTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 28sp,
        letterSpacing = 8.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = DisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16sp,
        letterSpacing = 3.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14sp,
        lineHeight = 20.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10sp,
        letterSpacing = 2.sp,
    ),
)

/** HUD micro-label: uppercase mono with wide tracking. */
val MonoLabel = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 10sp,
    letterSpacing = 2.sp,
)

/** Monospace readout for status / transcript lines. */
val MonoReadout = TextStyle(
    fontFamily = MonoFamily,
    fontWeight = FontWeight.Normal,
    fontSize = 13sp,
    lineHeight = 18.sp,
)
