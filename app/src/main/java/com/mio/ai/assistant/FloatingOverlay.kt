package com.mio.ai.assistant

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.mio.ai.BuildConfig
import com.mio.ai.ui.overlay.FloatingOrbContent
import com.mio.ai.ui.overlay.VoicePanelContent
import com.mio.ai.ui.vm.AssistantStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * System-overlay windows owned by [AssistantService]: the small floating orb
 * and the compact voice panel. Only ever created when
 * [Settings.canDrawOverlays] holds; every WindowManager call is guarded so a
 * revoked permission or dead window token degrades to "no overlay" instead
 * of crashing.
 *
 * Exactly one window is visible at a time: tapping the orb swaps it for the
 * panel, and the panel collapses back to the orb when the command finishes
 * (status returns to IDLE with nothing awaiting confirmation).
 */
class FloatingOverlay(
    private val context: Context,
    private val core: AssistantCore,
    private val scope: CoroutineScope,
    private val onOpenApp: () -> Unit,
    private val onOpenPermissions: (highlight: String?) -> Unit,
) {
    private val wm: WindowManager? = context.getSystemService(WindowManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density

    private var orbView: ComposeView? = null
    private var panelView: ComposeView? = null
    private var orbX = 0
    private var orbY = 0
    private var collapseJob: Job? = null

    private val _panelVisible = MutableStateFlow(false)
    val panelVisible: StateFlow<Boolean> = _panelVisible.asStateFlow()

    /** Incremented to pulse the orb ("grab me") after a long-press. */
    private val _movePulse = MutableStateFlow(0)
    val movePulse: StateFlow<Int> = _movePulse.asStateFlow()

    val hasOverlayPermission: Boolean
        get() = Settings.canDrawOverlays(context)

    // ------------------------------------------------------------------- orb

    /** Show the floating orb (no-op when the panel is up or permission is off). */
    fun ensureOrb() {
        if (!hasOverlayPermission) {
            removeOrb()
            return
        }
        if (orbView != null || _panelVisible.value) return
        try {
            val (sx, sy) = screenSize()
            val size = orbSizePx()
            orbX = (prefs.getFloat(KEY_FX, 1f) * (sx - size)).toInt()
            orbY = (prefs.getFloat(KEY_FY, 0.38f) * (sy - size)).toInt()
            clampOrb(sx, sy, size)
            val view = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    FloatingOrbContent(
                        core = core,
                        movePulse = movePulse,
                        onTap = {
                            showPanel()
                            // Tapping into a live session just reveals it; a
                            // fresh tap from idle starts listening.
                            val s = core.status.value
                            if (s == AssistantStatus.IDLE || s == AssistantStatus.ERROR) {
                                core.onMicPress()
                            }
                        },
                        onMoveStart = { _movePulse.value += 1 },
                        onMove = { dx, dy -> moveOrbBy(dx, dy) },
                        onMoveEnd = { savePosition() },
                    )
                }
            }
            wm?.addView(view, baseParams("MioOrb").apply {
                gravity = Gravity.TOP or Gravity.START
                x = orbX
                y = orbY
            })
            orbView = view
            debug("orb shown at $orbX,$orbY")
        } catch (e: Exception) {
            debug("ensureOrb failed: ${e.message}")
            orbView = null
        }
    }

    fun removeOrb() {
        orbView?.let { runCatching { wm?.removeView(it) } }
        orbView = null
    }

    /** Rotation / fold / multi-window: keep the orb on screen. */
    fun reclamp() {
        if (orbView == null) return
        val (sx, sy) = screenSize()
        clampOrb(sx, sy, orbSizePx())
        applyOrbPosition()
    }

    private fun moveOrbBy(dxPx: Float, dyPx: Float) {
        val (sx, sy) = screenSize()
        orbX += dxPx.toInt()
        orbY += dyPx.toInt()
        clampOrb(sx, sy, orbSizePx())
        applyOrbPosition()
    }

    private fun clampOrb(sx: Int, sy: Int, size: Int) {
        orbX = orbX.coerceIn(0, (sx - size).coerceAtLeast(0))
        orbY = orbY.coerceIn(0, (sy - size).coerceAtLeast(0))
    }

    private fun applyOrbPosition() {
        val view = orbView ?: return
        (view.layoutParams as? WindowManager.LayoutParams)?.let { params ->
            params.x = orbX
            params.y = orbY
            runCatching { wm?.updateViewLayout(view, params) }
        }
    }

    private fun savePosition() {
        val (sx, sy) = screenSize()
        val size = orbSizePx()
        prefs.edit()
            .putFloat(KEY_FX, orbX / (sx - size).coerceAtLeast(1).toFloat())
            .putFloat(KEY_FY, orbY / (sy - size).coerceAtLeast(1).toFloat())
            .apply()
    }

    // ------------------------------------------------------------------ panel

    fun showPanel() {
        if (!hasOverlayPermission) {
            onOpenApp()
            return
        }
        if (_panelVisible.value) return
        _panelVisible.value = true
        removeOrb()
        try {
            val view = ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    VoicePanelContent(
                        core = core,
                        onClose = { hidePanel() },
                        onOpenApp = onOpenApp,
                        onOpenPermissions = onOpenPermissions,
                    )
                }
            }
            wm?.addView(view, baseParams("MioPanel").apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = (PANEL_MARGIN_DP * density).toInt()
            })
            panelView = view
            watchForIdle()
            debug("panel shown")
        } catch (e: Exception) {
            debug("showPanel failed: ${e.message}")
            panelView = null
            _panelVisible.value = false
            ensureOrb()
        }
    }

    fun hidePanel() {
        collapseJob?.cancel()
        collapseJob = null
        panelView?.let { runCatching { wm?.removeView(it) } }
        panelView = null
        if (_panelVisible.value) {
            _panelVisible.value = false
            ensureOrb()
        }
    }

    /**
     * After a command finishes (IDLE with nothing awaiting confirmation),
     * collapse back to the orb. A pending confirmation keeps the panel up —
     * the user must say Yes/No or tap a button.
     */
    private fun watchForIdle() {
        collapseJob?.cancel()
        collapseJob = scope.launch {
            var active = false
            core.status.collect { s ->
                if (s != AssistantStatus.IDLE) {
                    active = true
                } else if (active && core.pendingPlan.value == null) {
                    hidePanel()
                }
            }
        }
    }

    // ------------------------------------------------------------------ common

    fun destroy() {
        collapseJob?.cancel()
        collapseJob = null
        _panelVisible.value = false
        removeOrb()
        panelView?.let { runCatching { wm?.removeView(it) } }
        panelView = null
    }

    private fun baseParams(title: String) =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { setTitle("MioAI:$title") }

    private fun orbSizePx(): Int = (ORB_BOX_DP * density).toInt()

    private fun screenSize(): Pair<Int, Int> {
        val manager = wm
        if (Build.VERSION.SDK_INT >= 30 && manager != null) {
            val b = manager.currentWindowMetrics.bounds
            return b.width() to b.height()
        }
        val metrics = context.resources.displayMetrics
        return metrics.widthPixels to metrics.heightPixels
    }

    private fun debug(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    companion object {
        private const val TAG = "FloatingOverlay"
        private const val PREFS = "mio_overlay"
        private const val KEY_FX = "orb_fx"
        private const val KEY_FY = "orb_fy"
        private const val ORB_BOX_DP = 76
        private const val PANEL_MARGIN_DP = 48
    }
}
