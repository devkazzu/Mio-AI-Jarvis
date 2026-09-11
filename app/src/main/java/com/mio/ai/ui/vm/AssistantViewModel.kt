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
import com.mio.ai.data.ListeningMode
import com.mio.ai.data.MioSettings
import com.mio.ai.voice.MioTts
import com.mio.ai.voice.SpeechListener
import com.mio.ai.voice.WakeWordService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.withLock
import java.util.Calendar
import java.util.concurrent.atomic.AtomicLong

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
 * Orchestrates the assistant loop: mic → speech → router → (confirm?) →
 * engine → TTS, with live UI updates, STOP support, continuous listening,
 * and a persistent action log.
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

    private val _actionLog = MutableStateFlow<List<LoggedAction>>(emptyList())
    val actionLog: StateFlow<List<LoggedAction>> = _actionLog.asStateFlow()

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

    private val _ttsVoices = MutableStateFlow<List<String>>(emptyList())
    val ttsVoices: StateFlow<List<String>> = _ttsVoices.asStateFlow()

    private val listener = SpeechListener(application.applicationContext)
    private val tts = MioTts(application.applicationContext)
    private val commandMutex = Mutex()
    private val ids = AtomicLong(1)
    private var speakJob: Job? = null
    private var executionJob: Job? = null
    private var noResultStreak = 0
    private var booted = false

    init {
        listener.onFinalResult = { handleUtterance(it) }
        listener.onError = { message, fatal -> onListenError(message, fatal) }
        tts.init { refreshVoices() }
        viewModelScope.launch { listener.rms.collect { _rms.value = it } }
        viewModelScope.launch { listener.partial.collect { _partial.value = it } }
        viewModelScope.launch {
            settings.collect { s ->
                tts.setRatePitch(s.speechRate, s.speechPitch)
                if (s.ttsVoiceName.isNotBlank()) tts.setVoiceByName(s.ttsVoiceName)
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
        if (!settings.value.keepHistory) conversation.clear()
        val name = settings.value.nickname?.let { ", $it" }.orEmpty()
        val hello = "Mio online$name. Tap the mic and tell me what to do."
        addEntry(EntryKind.ASSISTANT, hello)
        speak(hello, autoContinue = false)
    }

    /** Launched from the wake-word service. */
    fun onWake() {
        if (_status.value == AssistantStatus.IDLE) onMicPress()
    }

    private fun executionActive(): Boolean = executionJob?.isActive == true

    // ------------------------------------------------------------------- input

    fun onMicPress() {
        when (_status.value) {
            AssistantStatus.LISTENING -> {
                listener.cancel()
                _partial.value = ""
                _status.value = if (executionActive()) AssistantStatus.EXECUTING else AssistantStatus.IDLE
            }
            AssistantStatus.SPEAKING -> {
                speakJob?.cancel()
                tts.stop()
                _status.value = if (executionActive()) AssistantStatus.EXECUTING else AssistantStatus.IDLE
            }
            AssistantStatus.THINKING -> Unit // routing is instant — ignore taps
            AssistantStatus.EXECUTING -> startListening() // listen pass so "stop" works by voice
            AssistantStatus.IDLE, AssistantStatus.ERROR -> startListening()
        }
    }

    private fun startListening() {
        tts.stop()
        if (!listener.hasPermission()) {
            _status.value = AssistantStatus.ERROR
            addEntry(EntryKind.SYSTEM, "I need microphone access to hear you.", fixDestination = "mic")
            speak("I need microphone access to hear you. Tap Fix to grant it.", autoContinue = false)
            return
        }
        if (!listener.isAvailable()) {
            _status.value = AssistantStatus.ERROR
            addEntry(EntryKind.SYSTEM, "No speech recognizer found on this device.")
            speak("There's no speech recognizer on this device.", autoContinue = false)
            return
        }
        _partial.value = ""
        if (listener.startListening()) {
            _status.value = AssistantStatus.LISTENING
        } else if (!executionActive()) {
            _status.value = AssistantStatus.ERROR
        }
    }

    private fun onListenError(message: String, fatal: Boolean) {
        if (_status.value == AssistantStatus.LISTENING) {
            _status.value = if (executionActive()) AssistantStatus.EXECUTING else AssistantStatus.IDLE
        }
        _partial.value = ""
        val continuous = settings.value.listeningMode == ListeningMode.CONTINUOUS
        if (fatal) {
            noResultStreak = 0
            addEntry(EntryKind.SYSTEM, message, fixDestination = "mic")
            speak(message, autoContinue = false)
            return
        }
        // Continuous mode: silently retry twice, then say so and stop the loop.
        if (continuous) {
            noResultStreak++
            if (noResultStreak < 3) {
                startListening()
                return
            }
        }
        noResultStreak = 0
        speak(message, autoContinue = false)
    }

    /** Typed commands and quick actions take the same path as voice. */
    fun submitText(text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        if (_status.value == AssistantStatus.LISTENING) listener.cancel()
        handleUtterance(t)
    }

    /** Replay a Mio message out loud (Conversation screen). */
    fun speakMessage(text: String) {
        if (_status.value == AssistantStatus.LISTENING) listener.cancel()
        speak(text, autoContinue = false)
    }

    fun refreshVoices() {
        _ttsVoices.value = tts.englishVoiceNames()
    }

    // ------------------------------------------------------------------ router

    private fun handleUtterance(text: String) {
        noResultStreak = 0
        viewModelScope.launch {
            commandMutex.withLock { process(text) }
        }
    }

    private suspend fun process(text: String) {
        _status.value = AssistantStatus.THINKING
        _partial.value = ""
        addEntry(EntryKind.USER, text)
        val s = settings.value
        val useMemory = s.memoryEnabled
        if (useMemory) conversation.addUser(text)

        val runtime = app.resolveAi(s)
        val history = if (useMemory) conversation.toChatMessages(s.historyDepth) else emptyList()
        val decision = try {
            runtime.router.route(text, history)
        } catch (e: Exception) {
            CommandRouter.Decision.Say("My router glitched (${e.message}). Try again?")
        }

        // An execution is running: only "stop" (or no) interrupts it.
        if (executionActive()) {
            when (decision) {
                is CommandRouter.Decision.Cancel, is CommandRouter.Decision.ConfirmNo -> stopExecution()
                else -> {
                    val busy = "Still working on it — say “stop” to cancel."
                    addEntry(EntryKind.ASSISTANT, busy)
                    speak(busy, autoContinue = false)
                    if (_status.value == AssistantStatus.SPEAKING || _status.value == AssistantStatus.THINKING) {
                        // speak() will set SPEAKING; restore EXECUTING right after it starts.
                        _status.value = AssistantStatus.EXECUTING
                    }
                }
            }
            return
        }

        when (decision) {
            is CommandRouter.Decision.DoPlan -> {
                _pendingPlan.value = null
                clearAwaitingFlags()
                if (decision.plan.requiresConfirmation && s.confirmations) {
                    askConfirmation(decision.plan, useMemory)
                } else {
                    runPlan(decision.plan, useMemory)
                }
            }
            is CommandRouter.Decision.Say -> say(decision.text, useMemory, autoContinue = true)
            is CommandRouter.Decision.ConverseLocal -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val reply = LocalBrain.reply(
                    decision.text,
                    LocalBrain.Context(s.nickname, hour, runtime.planner != null, s.responseStyle),
                )
                say(reply, useMemory, autoContinue = true)
            }
            is CommandRouter.Decision.SetNickname -> {
                app.settingsRepo.setNickname(decision.name)
                say("Got it — I'll call you ${decision.name} from now on.", useMemory, autoContinue = true)
            }
            is CommandRouter.Decision.ConfirmYes -> {
                val pending = _pendingPlan.value
                if (pending != null) {
                    _pendingPlan.value = null
                    clearAwaitingFlags()
                    runPlan(pending, useMemory)
                } else {
                    say("There's nothing waiting for confirmation.", useMemory, autoContinue = true)
                }
            }
            is CommandRouter.Decision.ConfirmNo, is CommandRouter.Decision.Cancel -> {
                if (_pendingPlan.value != null) {
                    _pendingPlan.value = null
                    clearAwaitingFlags()
                    say("Okay, cancelled.", useMemory, autoContinue = true)
                } else {
                    tts.stop()
                    _status.value = AssistantStatus.IDLE
                    say("Okay.", useMemory, autoContinue = false)
                }
            }
        }
    }

    private suspend fun askConfirmation(plan: Plan, useMemory: Boolean) {
        _pendingPlan.value = plan
        val prompt = plan.confirmationPrompt ?: "Go ahead with: ${plan.summary}?"
        addEntry(
            EntryKind.ACTION, plan.summary, plan = plan,
            steps = plan.steps.map { StepUi(it.label, StepVisual.PENDING) },
            awaitingConfirm = true,
        )
        if (useMemory) conversation.addAssistant(prompt)
        speak(prompt, autoContinue = true)
    }

    fun confirmPending() {
        viewModelScope.launch {
            commandMutex.withLock {
                val pending = _pendingPlan.value ?: return@withLock
                _pendingPlan.value = null
                clearAwaitingFlags()
                runPlan(pending, settings.value.memoryEnabled)
            }
        }
    }

    fun dismissPending() {
        viewModelScope.launch {
            commandMutex.withLock {
                if (_pendingPlan.value == null) return@withLock
                _pendingPlan.value = null
                clearAwaitingFlags()
                say("Okay, cancelled.", settings.value.memoryEnabled, autoContinue = true)
            }
        }
    }

    // --------------------------------------------------------------- execution

    private suspend fun runPlan(plan: Plan, useMemory: Boolean) {
        _status.value = AssistantStatus.EXECUTING
        val entryId = ids.getAndIncrement()
        _entries.value = _entries.value + ChatEntry(
            id = entryId, kind = EntryKind.ACTION, text = plan.summary, plan = plan,
            steps = plan.steps.map { StepUi(it.label, StepVisual.PENDING) },
            running = true,
        )
        trimEntries()

        val timeoutMs = settings.value.actionTimeoutSec * 1000L
        executionJob = viewModelScope.launch {
            try {
                val result = engine.execute(plan, timeoutMs) { index, state, outcome ->
                    _ticker.value = when (state) {
                        ActionEngine.StepState.RUNNING -> plan.steps[index].label
                        ActionEngine.StepState.DONE -> null
                    }
                    updateSteps(entryId, index, state, outcome)
                }
                _ticker.value = null
                finishEntry(entryId, cancelled = false)
                logAction(entryId, plan, cancelled = false)
                if (useMemory) conversation.addAssistant(result.spokenSummary)
                result.steps.firstOrNull { !it.success && it.fixDestination != null }?.let { failed ->
                    addEntry(EntryKind.SYSTEM, failed.detail, fixDestination = failed.fixDestination)
                }
                if (result.allSucceeded && result.steps.size == 1 &&
                    result.steps.first().action is com.mio.ai.core.actions.Action.QueryDeviceStatus
                ) {
                    addEntry(EntryKind.ASSISTANT, result.steps.first().detail)
                }
                speak(result.spokenSummary, autoContinue = true)
            } catch (_: CancellationException) {
                _ticker.value = null
                finishEntry(entryId, cancelled = true)
                logAction(entryId, plan, cancelled = true)
                if (_status.value == AssistantStatus.EXECUTING) _status.value = AssistantStatus.IDLE
                speak("Stopped.", autoContinue = false)
            }
        }
    }

    /** STOP button / "stop": cooperatively cancel the running plan. */
    fun stopExecution() {
        val job = executionJob
        if (job?.isActive == true) {
            job.cancel()
        } else if (_status.value == AssistantStatus.EXECUTING) {
            _status.value = AssistantStatus.IDLE
        }
    }

    fun clearActionLog() {
        _actionLog.value = emptyList()
    }

    private fun logAction(entryId: Long, plan: Plan, cancelled: Boolean) {
        val entry = _entries.value.firstOrNull { it.id == entryId }
        val steps = entry?.steps ?: plan.steps.map { StepUi(it.label, StepVisual.CANCELLED) }
        val succeeded = !cancelled && steps.isNotEmpty() && steps.all { it.state == StepVisual.DONE_OK }
        _actionLog.value = (
            _actionLog.value + LoggedAction(
                id = ids.getAndIncrement(),
                summary = plan.summary,
                atMillis = entry?.atMillis ?: System.currentTimeMillis(),
                steps = steps,
                succeeded = succeeded,
                cancelled = cancelled,
            )
            ).takeLast(50)
    }

    private suspend fun say(text: String, useMemory: Boolean, autoContinue: Boolean) {
        addEntry(EntryKind.ASSISTANT, text)
        if (useMemory) conversation.addAssistant(text)
        speak(text, autoContinue)
    }

    // --------------------------------------------------------------------- tts

    private fun speak(text: String, autoContinue: Boolean) {
        speakJob?.cancel()
        val continuous = settings.value.listeningMode == ListeningMode.CONTINUOUS
        if (!settings.value.voiceReplies) {
            _status.value = AssistantStatus.IDLE
            if (autoContinue && continuous) startListening()
            return
        }
        _status.value = AssistantStatus.SPEAKING
        speakJob = viewModelScope.launch {
            try {
                // One silent retry: a transient engine hiccup must never strand the user.
                var err = attemptSpeak(text)
                if (err != null) err = attemptSpeak(text)
                if (err != null) {
                    // Voice failed twice — the reply is already visible as text; note it once.
                    // Never speak() the fallback itself: the engine is what just failed.
                    addEntry(EntryKind.SYSTEM, err)
                }
                if (_status.value == AssistantStatus.SPEAKING) {
                    if (executionActive()) {
                        _status.value = AssistantStatus.EXECUTING
                    } else {
                        _status.value = AssistantStatus.IDLE
                        if (autoContinue && continuous) startListening()
                    }
                }
            } catch (_: CancellationException) {
                // Superseded by a newer utterance or stopped — caller owns status.
            }
        }
    }

    /**
     * Speaks once with a bounded wait. Returns null when spoken, otherwise a
     * user-facing reason ("Speech unavailable …" / "Speech error …"). Engine
     * failures never throw here (only cancellation propagates), and the wait
     * is time-boxed so status can never wedge in SPEAKING.
     */
    private suspend fun attemptSpeak(text: String): String? {
        val done = CompletableDeferred<Unit>()
        var failed: String? = null
        tts.speak(
            text, enabled = true, flush = true,
            onDone = { if (!done.isCompleted) done.complete(Unit) },
            onError = { failed = it },
        )
        // ~speaking rate with headroom: status always returns even if the engine goes silent.
        val budgetMs = (text.length * 100L).coerceIn(30_000L, 150_000L)
        withTimeoutOrNull(budgetMs) { done.await() }
            ?: return "Speech timed out — showing text instead."
        return failed
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

    private fun finishEntry(entryId: Long, cancelled: Boolean) {
        _entries.value = _entries.value.map { e ->
            if (e.id != entryId) return@map e
            val steps = if (cancelled) {
                e.steps.map { s ->
                    if (s.state == StepVisual.PENDING || s.state == StepVisual.RUNNING) {
                        s.copy(state = StepVisual.CANCELLED)
                    } else {
                        s
                    }
                }
            } else {
                e.steps
            }
            e.copy(running = false, cancelled = cancelled, steps = steps)
        }
    }

    private fun clearAwaitingFlags() {
        _entries.value = _entries.value.map { it.copy(awaitingConfirm = false) }
    }

    // ---------------------------------------------------------------- settings

    fun clearHistory() {
        conversation.clear()
        _entries.value = emptyList()
        _actionLog.value = emptyList()
    }

    fun setWakeWord(on: Boolean) {
        viewModelScope.launch {
            app.settingsRepo.setWakeWord(on)
            if (on) WakeWordService.start(getApplication()) else WakeWordService.stop(getApplication())
        }
    }

    fun syncWakeService() {
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
