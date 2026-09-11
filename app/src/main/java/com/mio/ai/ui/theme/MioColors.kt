package com.mio.ai.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.mio.ai.data.MioThemePref

/**
 * Mio v2 palette — near-black foundation, ONE electric-cyan accent, violet
 * reserved for the thinking state. See design-system/mio-ai/DESIGN_SYSTEM.md.
 * Components read these tokens; raw color values in components are a bug.
 */
enum class MioThemeMode { MIDNIGHT, ABYSS }

fun themeModeOf(pref: String): MioThemeMode =
    if (pref == MioThemePref.ABYSS) MioThemeMode.ABYSS else MioThemeMode.MIDNIGHT

data class MioColors(
    val void: Color,
    val base: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val line: Color,
    val accent: Color,
    val accentDeep: Color,
    val violet: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
) {
    companion object {
        fun midnight() = MioColors(
            void = Color(0xFF05070A),
            base = Color(0xFF080B10),
            surface = Color(0xFF0D1117),
            surfaceHigh = Color(0xFF121821),
            line = Color(0x14FFFFFF),
            accent = Color(0xFF22D3EE),
            accentDeep = Color(0xFF0284C7),
            violet = Color(0xFF8B5CF6),
            textPrimary = Color(0xFFF1F5F9),
            textSecondary = Color(0xFF9AA7BD),
            textMuted = Color(0xFF5D6B84),
            success = Color(0xFF34D399),
            warning = Color(0xFFFBBF24),
            danger = Color(0xFFF87171),
        )

        fun abyss() = MioColors(
            void = Color(0xFF020304),
            base = Color(0xFF05070A),
            surface = Color(0xFF0A0D12),
            surfaceHigh = Color(0xFF10151D),
            line = Color(0x12FFFFFF),
            accent = Color(0xFF22D3EE),
            accentDeep = Color(0xFF0284C7),
            violet = Color(0xFFA78BFA),
            textPrimary = Color(0xFFF4F7FB),
            textSecondary = Color(0xFFA4B0C6),
            textMuted = Color(0xFF7E8EA8),
            success = Color(0xFF34D399),
            warning = Color(0xFFFBBF24),
            danger = Color(0xFFF87171),
        )
    }
}

val LocalMioColors = staticCompositionLocalOf { MioColors.midnight() }
