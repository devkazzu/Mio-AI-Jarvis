package com.mio.ai.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mio.ai.MioApplication
import com.mio.ai.assistant.AssistantService
import com.mio.ai.core.actions.Plan
import com.mio.ai.core.ai.BrainState
import com.mio.ai.data.MioSettings
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Assistant status shown across Home / Conversation / Actions. */
enum class AssistantStatus { IDLE, LISTENING, THINKING, SPEAKING, EXECUTING, ERROR }

enum class StepVisual { PENDING, RUNNING, DONE_OK, DONE_FAIL, CANCELLED }

data class StepUi(val label: String, val state: StepVisual, val detail: String? = null)

enum class EntryKind { USER, ASSISTANT, ACTION, SYSTEM }

data class ChatEntry(
    val id: Long,
    val kind: EntryKind,
    val text: String,
    val atMillis: Long = System.currentTimeMillis(),
    val plan: Plan? = null,
    val steps: List<StepUi> = emptyList(),
    /** Set when a confirmation decision is still open on this card. */
    val awaitingConfirm: Boolean = false,
    /** True while this card's plan is executing (STOP available). */
    val running: Boolean = false,
    /** True when the run was stopped by the user. */
    val cancelled: Boolean = false,
    /** Permissions destination for the Fix button (SYSTEM entries). */
    val fixDestination: String? = null,
)

/** Completed (or stopped) run kept in the Actions log. */
data class LoggedAction(
    val id: Long,
    val summary: String,
    val atMillis: Long,
    val steps: List<StepUi>,
    val succeeded: Boolean,
    val cancelled: Boolean,
)

/**
 * Thin UI façade over the app-scoped [com.mio.ai.assistant.AssistantCore].
 *
 * The pipeline (mic → router → engine → TTS) lives in the core so the
 * activity UI and the background service share one recognizer, one TTS
 * engine, and one conversation — this ViewModel only forwards state and
 * user intents. It owns no voice resources and must never destroy them.
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MioApplication
    private val core = app.assistant

    val settings: StateFlow<MioSettings> = core.settings
    val a11yConnected: StateFlow<Boolean> = core.a11yConnected

    val status: StateFlow<AssistantStatus> = core.status
    val entries: StateFlow<List<ChatEntry>> = core.entries
    val actionLog: StateFlow<List<LoggedAction>> = core.actionLog
    val partial: StateFlow<String> = core.partial
    val rms: StateFlow<Float> = core.rms
    val ticker: StateFlow<String?> = core.ticker
    val pendingPlan: StateFlow<Plan?> = core.pendingPlan
    val brainState: StateFlow<BrainState> = core.brainState
    val ttsVoices: StateFlow<List<String>> = core.ttsVoices

    /** True while the background foreground-service is actually running. */
    val backgroundRunning: StateFlow<Boolean> = AssistantService.running

    /** One-time greeting when the HUD first appears. */
    fun onBoot() = core.onBoot()

    /** Launched from the wake-word service. */
    fun onWake() = core.onWake()

    fun onMicPress() = core.onMicPress()

    /** Typed commands and quick actions take the same path as voice. */
    fun submitText(text: String) = core.submitText(text)

    /** Replay a Mio message out loud (Conversation screen). */
    fun speakMessage(text: String) = core.speakMessage(text)

    fun refreshVoices() = core.refreshVoices()

    fun refreshBrainState() = core.refreshBrainState()

    fun confirmPending() = core.confirmPending()

    fun dismissPending() = core.dismissPending()

    /** STOP button / "stop": cooperatively cancel the running plan. */
    fun stopExecution() = core.stopExecution()

    fun clearActionLog() = core.clearActionLog()

    fun clearHistory() = core.clearHistory()

    fun setWakeWord(on: Boolean) = core.setWakeWord(on)

    fun syncWakeService() = core.syncWakeService()

    fun setBackgroundAssistant(on: Boolean) {
        viewModelScope.launch {
            app.settingsRepo.setBackgroundAssistant(on)
            if (on) AssistantService.start(getApplication()) else AssistantService.stop(getApplication())
        }
    }
}
