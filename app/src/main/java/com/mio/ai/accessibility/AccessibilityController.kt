package com.mio.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityNodeInfo
import com.mio.ai.core.actions.ScrollDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Singleton bridge to [MioAccessibilityService]. All UI-automation primitives
 * live here: find/click by text, type, scroll, wait, global nav, gestures.
 */
object AccessibilityController {

    @Volatile
    private var service: MioAccessibilityService? = null

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage: StateFlow<String?> = _foregroundPackage.asStateFlow()

    fun bind(svc: MioAccessibilityService) {
        service = svc
        _isConnected.value = true
    }

    fun unbind(svc: MioAccessibilityService) {
        if (service === svc) {
            service = null
            _isConnected.value = false
        }
    }

    fun onForegroundApp(packageName: String?, className: String?) {
        if (!packageName.isNullOrBlank()) _foregroundPackage.value = packageName
    }

    /** True when the user enabled Mio's service in system Settings (survives reboots). */
    fun isServiceEnabledInSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val me = "${context.packageName}/${MioAccessibilityService::class.java.canonicalName}"
        return enabled.split(':').any { it.equals(me, ignoreCase = true) }
    }

    // ------------------------------------------------------------- primitives

    /** Tap the first node whose text/label matches. Checks text AND content-description. */
    suspend fun tapText(text: String, exact: Boolean = false, timeoutMs: Long = 4_000): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val svc = service ?: return false
            val root = runCatching { svc.rootInActiveWindow }.getOrNull()
            if (root != null) {
                val node = findNode(root) { matchesText(it, text, exact) }
                if (node != null) {
                    val clicked = clickNode(node)
                    runCatching { node.recycle() }
                    if (clicked) return true
                }
                delay(300)
            } else {
                delay(350)
            }
        }
        return false
    }

    /** Type into the focused/first editable field, optionally tapping a field hint first. */
    suspend fun typeText(text: String, fieldHint: String?, submit: Boolean): Boolean {
        val svc = service ?: return false
        if (!fieldHint.isNullOrBlank()) {
            tapText(fieldHint, exact = false, timeoutMs = 2_500)
            delay(400)
        }
        repeat(4) { attempt ->
            val root = runCatching { svc.rootInActiveWindow }.getOrNull()
            val target = root?.let { r ->
                runCatching { r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
                    ?: findNode(r) { n ->
                        runCatching { n.isEditable }.getOrDefault(false) ||
                            runCatching { (n.className ?: "").contains("EditText") }.getOrDefault(false)
                    }
            }
            if (target != null) {
                val args = Bundle().apply {
                    putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                        text,
                    )
                }
                val ok = runCatching {
                    target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }.getOrDefault(false)
                if (ok && submit) {
                    delay(300)
                    // No public IME-action API exists for accessibility services:
                    // fall back to tapping a visible Search/Go/Send/Done
                    // affordance (best effort — the text stays typed either way).
                    tapImeAction()
                    delay(300)
                }
                runCatching { target.recycle() }
                if (ok) return true
            }
            if (attempt < 3) delay(500)
        }
        return false
    }

    suspend fun scroll(direction: ScrollDirection, times: Int): Boolean {
        repeat(times.coerceIn(1, 10)) {
            if (!scrollOnce(direction)) return false
            delay(350)
        }
        return true
    }

    private suspend fun scrollOnce(direction: ScrollDirection): Boolean {
        val svc = service ?: return false
        if (direction == ScrollDirection.UP || direction == ScrollDirection.DOWN) {
            val root = runCatching { svc.rootInActiveWindow }.getOrNull()
            val target = root?.let {
                findNode(it) { n -> runCatching { n.isScrollable }.getOrDefault(false) }
            }
            if (target != null) {
                val action = if (direction == ScrollDirection.DOWN) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val ok = runCatching { target.performAction(action) }.getOrDefault(false)
                runCatching { target.recycle() }
                if (ok) return true
            }
        }
        // Gesture fallback (horizontal always; vertical when no scrollable node).
        return swipe(direction)
    }

    suspend fun waitForText(text: String, timeoutMs: Long): Boolean {
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < deadline) {
            val svc = service ?: return false
            val root = runCatching { svc.rootInActiveWindow }.getOrNull()
            if (root != null) {
                val node = findNode(root) { matchesText(it, text, exact = false) }
                if (node != null) {
                    runCatching { node.recycle() }
                    return true
                }
                delay(300)
            } else {
                delay(350)
            }
        }
        return false
    }

    /**
     * Tap the top-most plausible search result: first clickable node with real
     * text in the content region (below the search bar, above the nav bar).
     */
    suspend fun tapFirstResult(timeoutMs: Long = 6_000): Boolean {
        val svc = service ?: return false
        val metrics = svc.resources.displayMetrics
        val deadline = SystemClock.uptimeMillis() + timeoutMs
        val chrome = Regex("^(search|back|cancel|done|go|ok|allow|deny|home|close)$", RegexOption.IGNORE_CASE)
        while (SystemClock.uptimeMillis() < deadline) {
            val root = runCatching { svc.rootInActiveWindow }.getOrNull()
            if (root != null) {
                val candidates = collectNodes(root) { n ->
                    val t = runCatching { n.text?.toString() }.getOrNull().orEmpty()
                    if (t.length < 2 || chrome.matches(t.trim())) return@collectNodes false
                    if (!runCatching { n.isVisibleToUser }.getOrDefault(false)) return@collectNodes false
                    val bounds = Rect()
                    runCatching { n.getBoundsInScreen(bounds) }
                    if (bounds.isEmpty) return@collectNodes false
                    val cy = bounds.exactCenterY()
                    if (cy < metrics.heightPixels * 0.12f || cy > metrics.heightPixels * 0.94f) {
                        return@collectNodes false
                    }
                    runCatching { n.isClickable }.getOrDefault(false) || hasClickableAncestor(n)
                }.sortedBy { n ->
                    val b = Rect()
                    runCatching { n.getBoundsInScreen(b) }
                    b.exactCenterY()
                }
                val first = candidates.firstOrNull()
                candidates.forEach { if (it !== first) runCatching { it.recycle() } }
                if (first != null) {
                    val clicked = clickNode(first)
                    runCatching { first.recycle() }
                    if (clicked) return true
                }
                delay(400)
            } else {
                delay(400)
            }
        }
        return false
    }

    // ------------------------------------------------------- global navigation

    fun pressBack(): Boolean =
        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) == true

    fun goHome(): Boolean =
        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) == true

    fun openRecents(): Boolean =
        service?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) == true

    // ---------------------------------------------------------------- gestures

    suspend fun tapAt(x: Float, y: Float): Boolean {
        val svc = service ?: return false
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 60)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchAwait(svc, gesture, 1_500)
    }

    private suspend fun swipe(direction: ScrollDirection): Boolean {
        val svc = service ?: return false
        val m = svc.resources.displayMetrics
        val cx = m.widthPixels / 2f
        val cy = m.heightPixels / 2f
        val dist = (minOf(m.widthPixels, m.heightPixels) * 0.38f).coerceAtLeast(220f)
        val (fromX, fromY, toX, toY) = when (direction) {
            // Content scroll down == finger moves up.
            ScrollDirection.DOWN -> arrayOf(cx, cy + dist / 2, cx, cy - dist / 2)
            ScrollDirection.UP -> arrayOf(cx, cy - dist / 2, cx, cy + dist / 2)
            ScrollDirection.RIGHT -> arrayOf(cx - dist / 2, cy, cx + dist / 2, cy)
            ScrollDirection.LEFT -> arrayOf(cx + dist / 2, cy, cx - dist / 2, cy)
        }
        val path = Path().apply {
            moveTo(fromX, fromY)
            lineTo(toX, toY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 320)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchAwait(svc, gesture, 2_000)
    }

    private suspend fun dispatchAwait(
        svc: AccessibilityService,
        gesture: GestureDescription,
        timeoutMs: Long,
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { cont ->
            val dispatched = runCatching {
                svc.dispatchGesture(
                    gesture,
                    object : AccessibilityService.GestureResultCallback() {
                        override fun onCompleted(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onCancelled(gestureDescription: GestureDescription?) {
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                    null,
                )
            }.getOrDefault(false)
            if (!dispatched && cont.isActive) cont.resume(false)
        }
    } ?: false

    // ----------------------------------------------------------------- helpers

    private fun matchesText(node: AccessibilityNodeInfo, query: String, exact: Boolean): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return false
        val texts = listOf(
            runCatching { node.text?.toString() }.getOrNull(),
            runCatching { node.contentDescription?.toString() }.getOrNull(),
            runCatching { node.hintText?.toString() }.getOrNull(),
        ).filterNotNull()
        return texts.any {
            if (exact) it.equals(q, ignoreCase = true) else it.contains(q, ignoreCase = true)
        }
    }

    /**
     * Breadth-first search; recycles every visited node except the match
     * (and the root, which the caller does not own either — we recycle it).
     */
    private fun findNode(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        val visited = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        visited.add(root)
        var match: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty() && match == null) {
            val n = queue.removeFirst()
            if (runCatching { predicate(n) }.getOrDefault(false)) {
                match = n
                break
            }
            val count = runCatching { n.childCount }.getOrDefault(0)
            for (i in 0 until count) {
                val child = runCatching { n.getChild(i) }.getOrNull()
                if (child != null) {
                    queue.add(child)
                    visited.add(child)
                }
            }
        }
        visited.forEach { if (it !== match) runCatching { it.recycle() } }
        return match
    }

    private fun collectNodes(
        root: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): List<AccessibilityNodeInfo> {
        val matched = ArrayList<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val visited = ArrayList<AccessibilityNodeInfo>()
        visited.add(root)
        while (queue.isNotEmpty()) {
            val n = queue.removeFirst()
            if (runCatching { predicate(n) }.getOrDefault(false)) matched.add(n)
            val count = runCatching { n.childCount }.getOrDefault(0)
            for (i in 0 until count) {
                val child = runCatching { n.getChild(i) }.getOrNull()
                if (child != null) {
                    queue.add(child)
                    visited.add(child)
                }
            }
            if (matched.size >= 12) break
        }
        visited.forEach { if (it !in matched) runCatching { it.recycle() } }
        return matched
    }

    private fun hasClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var cursor: AccessibilityNodeInfo? = runCatching { node.parent }.getOrNull()
        var found = false
        repeat(5) {
            val c = cursor ?: return found
            if (runCatching { c.isClickable }.getOrDefault(false)) found = true
            val next = runCatching { c.parent }.getOrNull()
            runCatching { c.recycle() }
            cursor = next
            if (found) {
                var rest = cursor
                while (rest != null) {
                    val n = runCatching { rest.parent }.getOrNull()
                    runCatching { rest.recycle() }
                    rest = n
                }
                return true
            }
        }
        var rest = cursor
        while (rest != null) {
            val n = runCatching { rest.parent }.getOrNull()
            runCatching { rest.recycle() }
            rest = n
        }
        return found
    }

    /**
     * Single-scan fallback for TypeText(submit): tap a visible IME-style
     * affordance. False when none is on screen — the typed text remains.
     */
    private fun tapImeAction(): Boolean {
        val svc = service ?: return false
        val root = runCatching { svc.rootInActiveWindow }.getOrNull() ?: return false
        val labels = setOf("search", "go", "send", "done", "enter")
        val node = findNode(root) { n ->
            val t = (
                runCatching { n.text?.toString() }.getOrNull().orEmpty() + " " +
                    runCatching { n.contentDescription?.toString() }.getOrNull().orEmpty()
                ).trim().lowercase()
            t in labels
        } ?: return false
        val clicked = clickNode(node)
        runCatching { node.recycle() }
        return clicked
    }

    /** Clicks the node or its nearest clickable ancestor (recycles borrowed parents). */
    private fun clickNode(node: AccessibilityNodeInfo): Boolean {
        var cursor: AccessibilityNodeInfo = node
        var ownsCursor = false
        repeat(8) {
            if (runCatching { cursor.isClickable }.getOrDefault(false)) {
                val ok = runCatching {
                    cursor.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }.getOrDefault(false)
                if (ownsCursor) runCatching { cursor.recycle() }
                return ok
            }
            val parent = runCatching { cursor.parent }.getOrNull() ?: run {
                if (ownsCursor) runCatching { cursor.recycle() }
                return false
            }
            if (ownsCursor) runCatching { cursor.recycle() }
            cursor = parent
            ownsCursor = true
        }
        if (ownsCursor) runCatching { cursor.recycle() }
        return false
    }
}
