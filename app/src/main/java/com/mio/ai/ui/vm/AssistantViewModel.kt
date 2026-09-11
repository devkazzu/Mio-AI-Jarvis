package com.mio.ai.ui.vm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mio.ai.MioApplication
import com.mio.ai.accessibility.AccessibilityController
import com.mio.ai.core.actions.Plan
import com.mio.ai.core.actions.StepOutcome
import com.mio.ai.core.ai.ConversationStore
import com.mio.ai.core.ai.LocalBrain
import com.mio.ai.core.commands.CommandRouter
import com.mio.ai.core.engine.ActionEngine
import com.mio.ai.data.MioSettings
import com.mio.ai.voice.MioTts
import com.mio.ai.voice.SpeechListener
import com.mio.ai.voice.WakeWordService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

/** Assistant status shown in the HUD (skill: sealed state, never boolean flags). */
enum class AssistantStatus { IDLE, LISTENING, THINKING, SPEAKING, EXECUTING, ERROR }

enum class StepVisual { PENDING, RUNNING, DONE_OK, DONE_FAIL }

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
    /** Permissions destination for the Fix button (SYSTEM entries). */
    val fixDestination: String? = null,
)

/**
 * Orchestrates the whole assistant loop:
 * mic → speech → router → (confirm?) → engine → TTS, with live UI updates.
 */
class AssistantViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as MioApplication
    private val conversation: ConversationStore = app.conversationStore
    private val engine: ActionEngine = app.engine

    val settings: StateFlow<MioSettings> = app.settingsRepo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, MioSettings())

    val a11yConnected: StateFlow<Boolean> = AccessibilityController.isConnected

    private val _status = MutableStateFlow(AssistantStatus.IDLE)
    val status: StateFlow<AssistantStatus> = _status.asStateFlow()

    private val _entries = MutableStateFlow<List<ChatEntry>>(emptyList())
    val entries: StateFlow<List<ChatEntry>> = _entries.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _rms = MutableStateFlow(0f)
    val rms: StateFlow<Float> = _rms.asStateFlow()

    /** Currently executing step label ("what Mio is doing"). Null when idle. */
    private val _ticker = MutableStateFlow<String?>(null)
    val ticker: StateFlow<String?> = _ticker.asStateFlow()

    private val _pendingPlan = MutableStateFlow<Plan?>(null)
    val pendingPlan: StateFlow<Plan?> = _pendingPlan.asStateFlow()

    private val _cloudLabel = MutableStateFlow("Offline brain")
    val cloudLabel: StateFlow<String> = _cloudLabel.asStateFlow()

    private val listener = SpeechListener(application.applicationContext)
    private val tts = MioTts(application.applicationContext)
    private val commandMutex = Mutex()
    private val ids = AtomicLong(1)
    private var speakJob: Job? = null
    private var booted = false

    init {
        listener.onFinalResult = { handleUtterance(it) }
        listener.onError = { message, fatal -> onListenError(message, fatal) }
        tts.init()
        viewModelScope.launch { listener.rms.collect { _rms.value = it } }
        viewModelScope.launch { listener.partial.collect { _partial.value = it } }
        viewModelScope.launch {
            settings.collect { s ->
                tts.setRatePitch(s.speechRate, s.speechPitch)
                val ai = app.resolveAi(s)
                _cloudLabel.value =
                    if (ai.planner != null) "Cloud brain · ${ai.client.describe}" else "Offline brain"
            }
        }
    }

    /** One-time greeting when the HUD first appears. */
    fun onBoot() {
        if (booted) return
        booted = true
        val name = settings.value.nickname?.let { ", $it" }.orEmpty()
        val hello = "Mio online$name. Tap the mic and tell me what to do."
        addEntry(EntryKind.ASSISTANT, hello)
        speak(hello)
    }

    /** Launched from the wake-word service. */
    fun onWake() {
        if (_status.value == AssistantStatus.IDLE) onMicPress()
    }

    // ------------------------------------------------------------------- input

    fun onMicPress() {
        when (_status.value) {
            AssistantStatus.LISTENING -> {
                listener.cancel()
                _status.value = AssistantStatus.IDLE
                _partial.value = ""
            }
            AssistantStatus.SPEAKING -> tts.stop().also { _status.value = AssistantStatus.IDLE }
            AssistantStatus.THINKING, AssistantStatus.EXECUTING -> Unit // busy — ignore taps
            AssistantStatus.IDLE, AssistantStatus.ERROR -> startListening()
        }
    }

    private fun startListening() {
        tts.stop()
        if (!listener.hasPermission()) {
            _status.value = AssistantStatus.ERROR
            addEntry(
                EntryKind.SYSTEM,
                "I need microphone access to hear you.",
                fixDestination = "mic",
            )
            speak("I need microphone access to hear you. Tap Fix to grant it.")
            return
        }
        if (!listener.isAvailable()) {
            _status.value = AssistantStatus.ERROR
            addEntry(EntryKind.SYSTEM, "No speech recognizer found on this device.")
            speak("There's no speech recognizer on this device.")
            return
        }
        _partial.value = ""
        if (listener.startListening()) {
            _status.value = AssistantStatus.LISTENING
        } else {
            _status.value = AssistantStatus.ERROR
        }
    }

    private fun onListenError(message: String, fatal: Boolean) {
        if (_status.value == AssistantStatus.LISTENING) _status.value = AssistantStatus.IDLE
        _partial.value = ""
        if (fatal) {
            addEntry(EntryKind.SYSTEM, message, fixDestination = "mic")
        }
        // Speak short failures so hands-free users aren't left guessing.
        speak(message)
    }

    /** Typed commands and quick actions take the same path as voice. */
    fun submitText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (_status.value == AssistantStatus.LISTENING) listener.cancel()
        handleUtterance(t)
    }

    // ------------------------------------------------------------------ router

    private fun handleUtterance(text: String) {
        viewModelScope.launch {
            commandMutex.withLock { process(text) }
        }
    }

    private suspend fun process(text: String) {
        _status.value = AssistantStatus.THINKING
        _partial.value = ""
        addEntry(EntryKind.USER, text)
        conversation.addUser(text)

        val s = settings.value
        val runtime = app.resolveAi(s)
        val decision = try {
            runtime.router.route(text, conversation.toChatMessages())
        } catch (e: Exception) {
            CommandRouter.Decision.Say("My router glitched (${e.message}). Try again?")
        }

        when (decision) {
            is CommandRouter.Decision.DoPlan -> {
                // A fresh command supersedes any stale pending confirmation.
                _pendingPlan.value = null
                clearAwaitingFlags()
                if (decision.plan.requiresConfirmation && s.confirmations) {
                    askConfirmation(decision.plan)
                } else {
                    runPlan(decision.plan)
                }
            }
            is CommandRouter.Decision.Say -> say(decision.text)
            is CommandRouter.Decision.ConverseLocal -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val reply = LocalBrain.reply(
                    decision.text,
                    LocalBrain.Context(s.nickname, hour, runtime.planner != null),
                )
                say(reply)
            }
            is CommandRouter.Decision.SetNickname -> {
                app.settingsRepo.setNickname(decision.name)
                say("Got it — I'll call you ${decision.name} from now on.")
            }
            is CommandRouter.Decision.ConfirmYes -> {
                val pending = _pendingPlan.value
                if (pending != null) {
                    _pendingPlan.value = null
                    clearAwaitingFlags()
                    runPlan(pending)
                } else {
                    say("There's nothing waiting for confirmation.")
                }
            }
            is CommandRouter.Decision.ConfirmNo, is CommandRouter.Decision.Cancel -> {
                if (_pendingPlan.value != null) {
                    _pendingPlan.value = null
                    clearAwaitingFlags()
                    say("Okay, cancelled.")
                } else {
                    tts.stop()
                    _status.value = AssistantStatus.IDLE
                    say("Okay.")
                }
            }
        }
    }

    private suspend fun askConfirmation(plan: Plan) {
        _pendingPlan.value = plan
        val prompt = plan.confirmationPrompt ?: "Go ahead with: ${plan.summary}?"
        addEntry(
            EntryKind.ACTION, plan.summary, plan = plan,
            steps = plan.steps.map { StepUi(it.label, StepVisual.PENDING) },
            awaitingConfirm = true,
        )
        conversation.addAssistant(prompt)
        speak(prompt)
    }

    fun confirmPending() {
        viewModelScope.launch {
            commandMutex.withLock {
                val pending = _pendingPlan.value ?: return@withLock
                _pendingPlan.value = null
                clearAwaitingFlags()
                runPlan(pending)
            }
        }
    }

    fun dismissPending() {
        viewModelScope.launch {
            commandMutex.withLock {
                if (_pendingPlan.value == null) return@withLock
                _pendingPlan.value = null
                clearAwaitingFlags()
                say("Okay, cancelled.")
            }
        }
    }

    private suspend fun runPlan(plan: Plan) {
        _status.value = AssistantStatus.EXECUTING
        val entryId = ids.getAndIncrement()
        _entries.value = _entries.value + ChatEntry(
            id = entryId, kind = EntryKind.ACTION, text = plan.summary, plan = plan,
            steps = plan.steps.map { StepUi(it.label, StepVisual.PENDING) },
        )
        trimEntries()

        val result = engine.execute(plan) { index, state, outcome ->
            _ticker.value = when (state) {
                ActionEngine.StepState.RUNNING -> plan.steps[index].label
                ActionEngine.StepState.DONE -> null
            }
            updateSteps(entryId, index, state, outcome)
        }

        _ticker.value = null
        conversation.addAssistant(result.spokenSummary)

        // Surface permission/service failures with a one-tap Fix.
        result.steps.firstOrNull { !it.success && it.fixDestination != null }?.let { failed ->
            addEntry(
                EntryKind.SYSTEM,
                failed.detail,
                fixDestination = failed.fixDestination,
            )
        }
        if (result.allSucceeded && result.steps.size == 1 &&
            result.steps.first().action is com.mio.ai.core.actions.Action.QueryDeviceStatus
        ) {
            // Status readouts also deserve a visible card, not just speech.
            addEntry(EntryKind.ASSISTANT, result.steps.first().detail)
        }
        speak(result.spokenSummary)
    }

    private suspend fun say(text: String) {
        addEntry(EntryKind.ASSISTANT, text)
        conversation.addAssistant(text)
        speak(text)
    }

    // --------------------------------------------------------------------- tts

    private fun speak(text: String) {
        speakJob?.cancel()
        val voiceOn = settings.value.voiceReplies
        if (!voiceOn) {
            _status.value = AssistantStatus.IDLE
            return
        }
        _status.value = AssistantStatus.SPEAKING
        speakJob = viewModelScope.launch {
            // Bridge the callback API into status updates.
            val done = kotlinx.coroutines.CompletableDeferred<Unit>()
            tts.speak(text, enabled = true, flush = true) { done.complete(Unit) }
            done.await()
            if (_status.value == AssistantStatus.SPEAKING) _status.value = AssistantStatus.IDLE
        }
    }

    // ------------------------------------------------------------------ entries

    private fun addEntry(
        kind: EntryKind,
        text: String,
        plan: Plan? = null,
        steps: List<StepUi> = emptyList(),
        awaitingConfirm: Boolean = false,
        fixDestination: String? = null,
    ) {
        _entries.value = _entries.value + ChatEntry(
            id = ids.getAndIncrement(), kind = kind, text = text, plan = plan,
            steps = steps, awaitingConfirm = awaitingConfirm, fixDestination = fixDestination,
        )
        trimEntries()
    }

    private fun trimEntries() {
        val list = _entries.value
        if (list.size > 200) _entries.value = list.takeLast(200)
    }

    private fun updateSteps(
        entryId: Long,
        index: Int,
        state: ActionEngine.StepState,
        outcome: StepOutcome?,
    ) {
        _entries.value = _entries.value.map { e ->
            if (e.id != entryId) return@map e
            val updated = e.steps.mapIndexed { i, s ->
                if (i != index) return@mapIndexed s
                when (state) {
                    ActionEngine.StepState.RUNNING -> s.copy(state = StepVisual.RUNNING)
                    ActionEngine.StepState.DONE -> s.copy(
                        state = if (outcome?.success == true) StepVisual.DONE_OK else StepVisual.DONE_FAIL,
                        detail = outcome?.detail,
                    )
                }
            }
            e.copy(steps = updated)
        }
    }

    private fun clearAwaitingFlags() {
        _entries.value = _entries.value.map { it.copy(awaitingConfirm = false) }
    }

    // ---------------------------------------------------------------- settings

    fun clearHistory() {
        conversation.clear()
        _entries.value = emptyList()
        addEntry(EntryKind.SYSTEM, "History cleared. Fresh start.")
    }

    fun setWakeWord(on: Boolean) {
        viewModelScope.launch {
            app.settingsRepo.setWakeWord(on)
            if (on) WakeWordService.start(getApplication()) else WakeWordService.stop(getApplication())
        }
    }

    fun syncWakeService() {
        // Called on boot of the UI: keep the service matching the toggle.
        viewModelScope.launch {
            if (settings.value.wakeWord) WakeWordService.start(getApplication())
        }
    }

    override fun onCleared() {
        listener.destroy()
        tts.shutdown()
        super.onCleared()
    }
}
