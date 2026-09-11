package com.mio.ai.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import com.mio.ai.data.AnimationPref

/**
 * Motion levels. FULL = everything · REDUCED = fades only (no rotation,
 * particles, pulse) · OFF = static. The OS animator-duration-scale = 0
 * (accessibility) forces OFF regardless of the in-app setting.
 */
enum class MotionLevel { FULL, REDUCED, OFF }

fun motionLevelOf(pref: String): MotionLevel = when (pref) {
    AnimationPref.REDUCED -> MotionLevel.REDUCED
    AnimationPref.OFF -> MotionLevel.OFF
    else -> MotionLevel.FULL
}

data class MioMotion(val level: MotionLevel) {
    val rotation: Boolean get() = level == MotionLevel.FULL
    val particles: Boolean get() = level == MotionLevel.FULL
    val pulse: Boolean get() = level != MotionLevel.OFF
    val waveform: Boolean get() = level != MotionLevel.OFF
    val transitions: Boolean get() = level != MotionLevel.OFF
}

/** Accent-intensity effect scaling (glow/bracket alphas). 0.3–1.0. */
data class MioFx(val accentIntensity: Float)

val LocalMioMotion = staticCompositionLocalOf { MioMotion(MotionLevel.FULL) }
val LocalMioFx = staticCompositionLocalOf { MioFx(0.85f) }

fun systemAnimationsDisabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(
        context.contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
    ) == 0f
}.getOrDefault(false)

/** Effective motion = in-app setting, overridden by the OS accessibility switch. */
@Composable
fun rememberEffectiveMotion(pref: String): MioMotion {
    val ctx = LocalContext.current
    return remember(pref) {
        val forcedOff = systemAnimationsDisabled(ctx)
        MioMotion(if (forcedOff) MotionLevel.OFF else motionLevelOf(pref))
    }
}
