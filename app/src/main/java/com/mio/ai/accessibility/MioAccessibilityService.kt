package com.mio.ai.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Accessibility bridge that lets Mio tap, scroll, type and navigate inside
 * other apps — but ONLY in direct response to the user's explicit commands.
 *
 * Privacy: node text is inspected on-device to find requested targets and is
 * never logged, stored or transmitted.
 */
class MioAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityController.bind(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            AccessibilityController.onForegroundApp(
                event.packageName?.toString(),
                event.className?.toString(),
            )
        }
    }

    override fun onInterrupt() {
        // Nothing latched — in-flight gestures are short-lived and time out.
    }

    override fun onDestroy() {
        AccessibilityController.unbind(this)
        super.onDestroy()
    }
}
