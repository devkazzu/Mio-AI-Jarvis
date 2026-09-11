package com.mio.ai.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Mio HUD palette — see design-system/mio-ai/MASTER.md (UI/UX Pro Max:
 * hud-sci-fi-fui + ai-native-ui). Components must read these tokens, never
 * raw hex.
 */
data class MioColors(
    val void: Color = Color(0xFF05070D),
    val abyss: Color = Color(0xFF0A0F1A),
    val glass: Color = Color(0xB80E1626),
    val userBubble: Color = Color(0xFF13233B),
    val hudLine: Color = Color(0x4700E5FF),
    val primary: Color = Color(0xFF00E5FF),
    val secondary: Color = Color(0xFF7C3AED),
    val textPrimary: Color = Color(0xFFE8F1FF),
    val textMuted: Color = Color(0xFF8B98B8),
    val success: Color = Color(0xFF34D399),
    val warning: Color = Color(0xFFFBBF24),
    val danger: Color = Color(0xFFF87171),
    val speaking: Color = Color(0xFF22D3EE),
    val executing: Color = Color(0xFFA78BFA),
)

val LocalMioColors = staticCompositionLocalOf { MioColors() }
