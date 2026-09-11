package com.mio.ai.system

/**
 * Result of one low-level device/app operation, before it is wrapped in a
 * [com.mio.ai.core.actions.StepOutcome] by the Action Engine.
 */
data class OpResult(
    val success: Boolean,
    /** User-facing detail (spoken + shown). */
    val detail: String,
    /**
     * Machine-readable reason: PERMISSION_DENIED, SERVICE_DISABLED,
     * APP_NOT_FOUND, CONTACT_NOT_FOUND, ELEMENT_NOT_FOUND, TIMEOUT,
     * NOT_SUPPORTED, UNKNOWN.
     */
    val failureReason: String? = null,
    /**
     * Permissions-screen destination for the "Fix" button:
     * mic, camera, bluetooth, wifi, dnd, write_settings, accessibility,
     * contacts, notifications.
     */
    val fixDestination: String? = null,
) {
    companion object {
        fun ok(detail: String) = OpResult(true, detail)
        fun fail(detail: String, reason: String, fix: String? = null) =
            OpResult(false, detail, reason, fix)
    }
}
