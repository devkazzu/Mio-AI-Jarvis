package com.mio.ai.core.actions

/**
 * Structured action model — the single contract between the [Command Router]
 * (rule parser + LLM planner) and the [Action Engine] that executes steps.
 *
 * Pure Kotlin: no Android imports, so the router stays unit-testable on the JVM.
 */

/** On/off/toggle switch used by device actions. */
enum class Switch { ON, OFF, TOGGLE }

/** Scroll direction for UI automation. */
enum class ScrollDirection { UP, DOWN, LEFT, RIGHT }

/** Volume adjustment. */
enum class VolumeDirection { UP, DOWN, MUTE, UNMUTE }

/** Every executable primitive. `sensitive=true` forces a user confirmation. */
sealed interface Action {
    /** Short human label shown in the UI ticker, e.g. "Turn on flashlight". */
    val label: String
    /** True when the action needs explicit user confirmation first. */
    val sensitive: Boolean get() = false

    // -- Device -----------------------------------------------------------------
    data class Torch(val switch: Switch) : Action {
        override val label: String = when (switch) {
            Switch.ON -> "Turn on flashlight"
            Switch.OFF -> "Turn off flashlight"
            Switch.TOGGLE -> "Toggle flashlight"
        }
    }

    data class Wifi(val switch: Switch) : Action {
        override val label: String = when (switch) {
            Switch.ON -> "Turn on Wi-Fi"
            Switch.OFF -> "Turn off Wi-Fi"
            Switch.TOGGLE -> "Toggle Wi-Fi"
        }
        // Android 10+ opens a system panel instead of toggling silently; confirm first.
        override val sensitive: Boolean = true
    }

    data class Bluetooth(val switch: Switch) : Action {
        override val label: String = when (switch) {
            Switch.ON -> "Turn on Bluetooth"
            Switch.OFF -> "Turn off Bluetooth"
            Switch.TOGGLE -> "Toggle Bluetooth"
        }
        override val sensitive: Boolean = true
    }

    data class Volume(val direction: VolumeDirection, val percent: Int? = null) : Action {
        override val label: String = when {
            percent != null -> "Set volume to $percent%"
            direction == VolumeDirection.UP -> "Turn volume up"
            direction == VolumeDirection.DOWN -> "Turn volume down"
            direction == VolumeDirection.MUTE -> "Mute volume"
            else -> "Unmute volume"
        }
    }

    data class DoNotDisturb(val switch: Switch) : Action {
        override val label: String = when (switch) {
            Switch.ON -> "Turn on Do Not Disturb"
            Switch.OFF -> "Turn off Do Not Disturb"
            Switch.TOGGLE -> "Toggle Do Not Disturb"
        }
        override val sensitive: Boolean = true
    }

    data class Brightness(val percent: Int? = null, val up: Boolean? = null) : Action {
        override val label: String = when {
            percent != null -> "Set brightness to $percent%"
            up == true -> "Increase brightness"
            up == false -> "Decrease brightness"
            else -> "Adjust brightness"
        }
    }

    // -- Apps & navigation ------------------------------------------------------
    /** Open an app by display name (fuzzy) or exact package. */
    data class OpenApp(val query: String, val packageName: String? = null) : Action {
        override val label: String = "Open $query"
    }

    /** Close the current foreground app (Back until it leaves, then Home). */
    data object CloseCurrentApp : Action {
        override val label: String = "Close current app"
    }

    data object GoHome : Action {
        override val label: String = "Go to Home screen"
    }

    data object PressBack : Action {
        override val label: String = "Go back"
    }

    data object OpenRecents : Action {
        override val label: String = "Open recent apps"
    }

    // -- Alarms / timers / camera ----------------------------------------------
    data class SetAlarm(val hour: Int, val minute: Int, val labelText: String? = null) : Action {
        override val label: String = "Set alarm for %02d:%02d".format(hour, minute)
    }

    data object ShowAlarms : Action {
        override val label: String = "Show alarms"
    }

    data class SetTimer(val seconds: Int, val labelText: String? = null) : Action {
        override val label: String = "Set timer"
    }

    data object OpenCamera : Action {
        override val label: String = "Open camera"
    }

    // -- Communication (always confirmed; Android opens dialer/composer,
    //    Mio never places calls or sends texts silently) -----------------------
    data class Dial(val recipient: String) : Action {
        override val label: String = "Call $recipient"
        override val sensitive: Boolean = true
    }

    data class ComposeSms(val recipient: String, val body: String? = null) : Action {
        override val label: String = "Text $recipient"
        override val sensitive: Boolean = true
    }

    // -- Accessibility UI automation --------------------------------------------
    data class TapText(val text: String, val exact: Boolean = false) : Action {
        override val label: String = "Tap “$text”"
    }

    data class TapFirstResult(val hint: String? = null) : Action {
        override val label: String = "Open the first result"
    }

    data class TypeText(
        val text: String,
        /** Optional field hint to focus first (e.g. "Search"). Null = focused field. */
        val fieldHint: String? = null,
        /** Press Enter/search after typing. */
        val submit: Boolean = false,
    ) : Action {
        override val label: String = "Type “${text.take(32)}”"
    }

    data class Scroll(val direction: ScrollDirection, val times: Int = 1) : Action {
        override val label: String = "Scroll ${direction.name.lowercase()}"
    }

    data class WaitForText(val text: String, val timeoutMs: Long = 8_000) : Action {
        override val label: String = "Wait for “$text”"
    }

    /** Fixed pause between UI steps (e.g. let a screen load). */
    data class Pause(val millis: Long) : Action {
        override val label: String = "Wait a moment"
    }

    // -- Read-only info ----------------------------------------------------------
    data object QueryTime : Action { override val label = "Check the time" }
    data object QueryDate : Action { override val label = "Check the date" }
    data object QueryBattery : Action { override val label = "Check battery" }
    data object QueryDeviceStatus : Action { override val label = "Check device status" }

    // -- Web & maps (plain VIEW intents — no special permission needed) ---------
    data class WebSearch(val query: String) : Action {
        override val label: String = "Search the web for $query"
    }

    data class Navigate(val destination: String) : Action {
        override val label: String = "Navigate to $destination"
    }
}

/**
 * An ordered multi-step plan produced by the Command Router.
 *
 * @param steps ordered actions to run.
 * @param summary one-line spoken/visible summary, e.g. "Opening Instagram and searching for cats".
 * @param requiresConfirmation true when any step is sensitive or the plan automates another app.
 * @param confirmationPrompt the exact question Mio asks before running.
 */
data class Plan(
    val steps: List<Action>,
    val summary: String,
    val requiresConfirmation: Boolean = false,
    val confirmationPrompt: String? = null,
) {
    init {
        require(steps.isNotEmpty()) { "Plan must contain at least one step" }
    }

    /** True if any step is flagged sensitive. */
    val hasSensitiveStep: Boolean get() = steps.any { it.sensitive }
}

/** Outcome of a single executed step. */
data class StepOutcome(
    val action: Action,
    val success: Boolean,
    /** Short user-facing detail, e.g. "Flashlight is on" / why it failed. */
    val detail: String,
    /**
     * Machine-readable failure reason for graceful handling:
     * PERMISSION_DENIED, SERVICE_DISABLED, APP_NOT_FOUND, ELEMENT_NOT_FOUND,
     * TIMEOUT, NOT_SUPPORTED, USER_CANCELLED, UNKNOWN.
     */
    val failureReason: String? = null,
    /** Optional permission/settings destination id for the "Fix" button. */
    val fixDestination: String? = null,
)

/** Aggregated result after the engine runs a whole plan. */
data class PlanResult(
    val plan: Plan,
    val steps: List<StepOutcome>,
    val allSucceeded: Boolean,
    /** Spoken summary for TTS. */
    val spokenSummary: String,
)
