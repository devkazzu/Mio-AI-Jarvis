package com.mio.ai.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mio.ai.BuildConfig
import com.mio.ai.MioApplication
import com.mio.ai.core.ai.EndpointValidation
import com.mio.ai.core.ai.OpenAiCompatibleClient
import com.mio.ai.core.ai.PingResult
import com.mio.ai.data.AnimationPref
import com.mio.ai.data.ListeningMode
import com.mio.ai.data.MioThemePref
import com.mio.ai.data.ResponseStyle
import com.mio.ai.ui.components.HudBackground
import com.mio.ai.ui.components.InfoNote
import com.mio.ai.ui.components.MioDivider
import com.mio.ai.ui.components.MioDropdown
import com.mio.ai.ui.components.MioTextField
import com.mio.ai.ui.components.MioToggle
import com.mio.ai.ui.components.MioTopBar
import com.mio.ai.ui.components.PrimaryButton
import com.mio.ai.ui.components.SecondaryButton
import com.mio.ai.ui.components.SectionHeader
import com.mio.ai.ui.components.SegmentedOptions
import com.mio.ai.ui.components.SettingRow
import com.mio.ai.ui.components.StepperRow
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.theme.mioMotion
import com.mio.ai.ui.vm.AssistantViewModel
import kotlinx.coroutines.launch

/** Cloud-brain connection state shown by the “Test connection” check. */
private sealed interface ConnState {
    data class Offline(val detail: String) : ConnState
    data object Connecting : ConnState
    data class Connected(val detail: String) : ConnState
    data class Failed(val detail: String) : ConnState
}

/**
 * Polished settings: AI · Voice · Automation · Appearance · Privacy · About.
 * Every control writes through to DataStore (and encrypted storage for keys).
 *
 * Security notes for the AI section: the stored API key is never displayed
 * (the field only accepts a replacement), travels only in the request
 * header, and clearing it asks for confirmation first.
 */
@Composable
fun SettingsScreen(
    vm: AssistantViewModel,
    onBack: () -> Unit,
    onOpenPermissions: (highlight: String?) -> Unit,
    onOpenLicenses: () -> Unit,
    onHelp: () -> Unit,
) {
    val mio = mioColors
    val dim = mioDimens
    val ctx = LocalContext.current
    val app = remember(ctx) { ctx.applicationContext as MioApplication }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cloudLabel by vm.cloudLabel.collectAsStateWithLifecycle()
    val voices by vm.ttsVoices.collectAsStateWithLifecycle()
    val a11yOn by vm.a11yConnected.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshVoices() }

    var url by remember(settings.aiBaseUrlOverride) { mutableStateOf(settings.aiBaseUrlOverride) }
    var model by remember(settings.aiModelOverride) { mutableStateOf(settings.aiModelOverride) }
    var apiKey by remember { mutableStateOf("") }
    var keyVisible by remember { mutableStateOf(false) }
    var keyTick by remember { mutableIntStateOf(0) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var connState by remember {
        mutableStateOf<ConnState>(ConnState.Offline("Not tested — tap TEST CONNECTION to verify."))
    }
    var testing by remember { mutableStateOf(false) }
    var showClearKeyDialog by remember { mutableStateOf(false) }
    var nickname by remember(settings.nickname) { mutableStateOf(settings.nickname.orEmpty()) }
    val keyStored = remember(keyTick) { app.secureKeys.hasApiKey() }

    // Cloud off always reads Offline (unless a test is mid-flight).
    val effectiveConn = if (!settings.useCloudAi && connState !is ConnState.Connecting) {
        ConnState.Offline("Cloud brain is off — Mio runs 100% on-device.")
    } else {
        connState
    }

    fun runConnectionTest() {
        val urlErr = EndpointValidation.baseUrlError(url)
        val modelErr = EndpointValidation.modelError(model)
        urlError = urlErr
        modelError = modelErr
        if (urlErr != null || modelErr != null) return
        val base = url.trim().ifBlank { settings.aiBaseUrlOverride.ifBlank { BuildConfig.MIO_AI_BASE_URL } }
        if (base.isBlank()) {
            connState = ConnState.Offline("No endpoint configured — Mio runs fully offline.")
            return
        }
        scope.launch {
            testing = true
            connState = ConnState.Connecting
            // Typed key wins so users can verify before saving; else stored, else developer key.
            val mdl = model.trim().ifBlank { settings.aiModelOverride.ifBlank { BuildConfig.MIO_AI_MODEL } }
            val key = if (apiKey.isNotBlank()) {
                apiKey
            } else {
                app.secureKeys.getApiKey().ifBlank { BuildConfig.MIO_AI_API_KEY }
            }
            connState = try {
                when (val r = OpenAiCompatibleClient(base, key, mdl).ping()) {
                    is PingResult.Ok -> ConnState.Connected(r.detail)
                    is PingResult.Fail -> ConnState.Failed(r.detail)
                }
            } catch (e: Exception) {
                ConnState.Failed("Test failed (${e.message?.take(120) ?: "unknown error"}).")
            }
            testing = false
        }
    }

    fun saveEndpoint() {
        val urlErr = EndpointValidation.baseUrlError(url)
        val modelErr = EndpointValidation.modelError(model)
        urlError = urlErr
        modelError = modelErr
        if (urlErr == null && modelErr == null) {
            scope.launch { app.settingsRepo.setAiEndpoint(url, model) }
        }
    }

    if (showClearKeyDialog) {
        AlertDialog(
            onDismissRequest = { showClearKeyDialog = false },
            title = { Text("Clear API key?", style = MioTypography.titleLarge, color = mio.textPrimary) },
            text = {
                Text(
                    "Mio will forget the stored key. The cloud brain falls back to the developer key, " +
                        "if one is configured, otherwise it stays offline. Offline commands keep working.",
                    style = MioTypography.bodyMedium,
                    color = mio.textSecondary,
                )
            },
            confirmButton = {
                SecondaryButton(
                    "Clear key",
                    {
                        scope.launch {
                            app.secureKeys.clearApiKey()
                            apiKey = ""
                            keyVisible = false
                            keyTick++
                            connState = ConnState.Offline("Key cleared — tap TEST CONNECTION to verify.")
                        }
                        showClearKeyDialog = false
                    },
                    destructive = true, compact = true,
                )
            },
            dismissButton = {
                SecondaryButton("Keep", { showClearKeyDialog = false }, compact = true)
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        HudBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = dim.gutter),
        ) {
            MioTopBar(title = "Settings", onBack = onBack)
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dim.xs),
            ) {
                // ------------------------------------------------------- AI
                SectionHeader(title = "AI")
                Text(cloudLabel.uppercase(), style = CaptionMono, color = mio.accent)
                InfoNote("OpenAI-compatible provider. Empty endpoint = fully offline; commands still work.")
                SettingRow(
                    title = "Use cloud brain",
                    desc = "Off means 100% on-device.",
                    onToggle = { scope.launch { app.settingsRepo.setUseCloudAi(!settings.useCloudAi) } },
                ) {
                    MioToggle(settings.useCloudAi) {
                        scope.launch { app.settingsRepo.setUseCloudAi(it) }
                    }
                }
                MioTextField(
                    value = url,
                    onChange = {
                        url = it
                        if (urlError != null) urlError = EndpointValidation.baseUrlError(it)
                    },
                    label = "Base URL",
                    placeholder = "https://api.openai.com/v1",
                    keyboard = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    onIme = { focus.moveFocus(FocusDirection.Down) },
                    error = urlError,
                )
                MioTextField(
                    value = model,
                    onChange = {
                        model = it
                        if (modelError != null) modelError = EndpointValidation.modelError(it)
                    },
                    label = "Model",
                    placeholder = "gpt-4o-mini",
                    imeAction = ImeAction.Next,
                    onIme = { focus.moveFocus(FocusDirection.Down) },
                    error = modelError,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(dim.md)) {
                    PrimaryButton("Save", ::saveEndpoint, Modifier.weight(1f), compact = true)
                    SecondaryButton(
                        "Test connection",
                        ::runConnectionTest,
                        Modifier.weight(1f),
                        enabled = settings.useCloudAi && !testing,
                        compact = true,
                    )
                }
                MioTextField(
                    value = apiKey,
                    onChange = { apiKey = it },
                    label = "API key · ${if (keyStored) "stored" else "not set"}",
                    placeholder = if (keyStored) "Enter a new key to replace it" else "sk-…",
                    password = !keyVisible,
                    keyboard = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onIme = { focus.clearFocus() },
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                contentDescription = if (keyVisible) "Hide API key" else "Show API key",
                                tint = mio.textSecondary,
                            )
                        }
                    },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(dim.md)) {
                    PrimaryButton(
                        "Save key",
                        {
                            scope.launch {
                                app.secureKeys.setApiKey(apiKey)
                                apiKey = ""
                                keyVisible = false
                                keyTick++
                                connState = ConnState.Offline("Key updated — tap TEST CONNECTION to verify.")
                            }
                        },
                        Modifier.weight(1f),
                        enabled = apiKey.isNotBlank(),
                        compact = true,
                    )
                    SecondaryButton(
                        "Clear key",
                        { showClearKeyDialog = true },
                        Modifier.weight(1f),
                        enabled = keyStored,
                        destructive = true,
                        compact = true,
                    )
                }
                InfoNote("Your key is stored encrypted on this device — and is never shown again.")
                ConnectionStatusRow(state = effectiveConn)
                SettingRow(title = "Response style", desc = "How much Mio says.") {
                    Spacer(Modifier.width(1.dp))
                }
                SegmentedOptions(
                    options = listOf(
                        ResponseStyle.CONCISE to "Concise",
                        ResponseStyle.BALANCED to "Balanced",
                        ResponseStyle.DETAILED to "Detailed",
                    ),
                    selected = settings.responseStyle,
                    onSelect = { scope.launch { app.settingsRepo.setResponseStyle(it) } },
                )
                SettingRow(
                    title = "Conversation memory",
                    desc = "Remember recent turns for follow-ups.",
                    onToggle = { scope.launch { app.settingsRepo.setMemoryEnabled(!settings.memoryEnabled) } },
                ) {
                    MioToggle(settings.memoryEnabled) {
                        scope.launch { app.settingsRepo.setMemoryEnabled(it) }
                    }
                }
                SettingRow(title = "Memory depth", desc = "Turns sent with each request.") {
                    StepperRow(
                        valueText = "${settings.historyDepth}",
                        onMinus = { scope.launch { app.settingsRepo.setHistoryDepth(settings.historyDepth - 1) } },
                        onPlus = { scope.launch { app.settingsRepo.setHistoryDepth(settings.historyDepth + 1) } },
                        minusEnabled = settings.historyDepth > 3,
                        plusEnabled = settings.historyDepth < 20,
                    )
                }
                MioDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = dim.sm))

                // ---------------------------------------------------- Voice
                SectionHeader(title = "Voice")
                MioDropdown(
                    label = "Voice",
                    options = voices,
                    selected = settings.ttsVoiceName,
                    onSelect = {
                        scope.launch { app.settingsRepo.setTtsVoice(it) }
                        vm.refreshVoices()
                    },
                )
                var rate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
                Text("SPEECH SPEED · ${"%.2f".format(rate)}×", style = CaptionMono, color = mio.textSecondary)
                Slider(
                    value = rate, onValueChange = { rate = it },
                    onValueChangeFinished = { scope.launch { app.settingsRepo.setSpeechRate(rate) } },
                    valueRange = 0.5f..2.0f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = mio.accent, activeTrackColor = mio.accent),
                    modifier = Modifier.semantics { contentDescription = "Speech speed, ${"%.2f".format(rate)} times" },
                )
                var pitch by remember(settings.speechPitch) { mutableFloatStateOf(settings.speechPitch) }
                Text("PITCH · ${"%.2f".format(pitch)}×", style = CaptionMono, color = mio.textSecondary)
                Slider(
                    value = pitch, onValueChange = { pitch = it },
                    onValueChangeFinished = { scope.launch { app.settingsRepo.setSpeechPitch(pitch) } },
                    valueRange = 0.5f..2.0f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = mio.accent, activeTrackColor = mio.accent),
                    modifier = Modifier.semantics { contentDescription = "Voice pitch, ${"%.2f".format(pitch)} times" },
                )
                SettingRow(
                    title = "Spoken replies",
                    desc = "Off means text only.",
                    onToggle = { scope.launch { app.settingsRepo.setVoiceReplies(!settings.voiceReplies) } },
                ) {
                    MioToggle(settings.voiceReplies) {
                        scope.launch { app.settingsRepo.setVoiceReplies(it) }
                    }
                }
                SettingRow(
                    title = "Wake word “Hey Mio”",
                    desc = "Always-on listening. Uses more battery.",
                    onToggle = { vm.setWakeWord(!settings.wakeWord) },
                ) {
                    MioToggle(settings.wakeWord) { vm.setWakeWord(it) }
                }
                SettingRow(title = "Listening mode", desc = "Continuous keeps the mic open between turns.") {
                    Spacer(Modifier.width(1.dp))
                }
                SegmentedOptions(
                    options = listOf(ListeningMode.TAP to "Tap to talk", ListeningMode.CONTINUOUS to "Continuous"),
                    selected = settings.listeningMode,
                    onSelect = { scope.launch { app.settingsRepo.setListeningMode(it) } },
                )
                MioDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = dim.sm))

                // ----------------------------------------------- Automation
                SectionHeader(title = "Automation", actionLabel = "Access", onAction = { onOpenPermissions(null) })
                SettingRow(
                    title = "UI Control",
                    desc = if (a11yOn) "On — Mio can tap, scroll and type." else "Off — tap Open to enable.",
                ) {
                    SecondaryButton(
                        if (a11yOn) "Open" else "Enable",
                        { onOpenPermissions("accessibility") },
                        compact = true,
                    )
                }
                SettingRow(
                    title = "Confirm before actions",
                    desc = "Ask first for calls, texts and automation.",
                    onToggle = { scope.launch { app.settingsRepo.setConfirmations(!settings.confirmations) } },
                ) {
                    MioToggle(settings.confirmations) {
                        scope.launch { app.settingsRepo.setConfirmations(it) }
                    }
                }
                SettingRow(title = "Action timeout", desc = "Max time per step before Mio gives up.") {
                    StepperRow(
                        valueText = "${settings.actionTimeoutSec}s",
                        onMinus = { scope.launch { app.settingsRepo.setActionTimeoutSec(settings.actionTimeoutSec - 1) } },
                        onPlus = { scope.launch { app.settingsRepo.setActionTimeoutSec(settings.actionTimeoutSec + 1) } },
                        minusEnabled = settings.actionTimeoutSec > 5,
                        plusEnabled = settings.actionTimeoutSec < 30,
                    )
                }
                MioDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = dim.sm))

                // ----------------------------------------------- Appearance
                SectionHeader(title = "Appearance")
                SettingRow(title = "Theme", desc = "Two calibrated dark themes.") {
                    Spacer(Modifier.width(1.dp))
                }
                SegmentedOptions(
                    options = listOf(MioThemePref.MIDNIGHT to "Midnight", MioThemePref.ABYSS to "Abyss"),
                    selected = settings.theme,
                    onSelect = { scope.launch { app.settingsRepo.setTheme(it) } },
                )
                var accent by remember(settings.accentIntensity) { mutableFloatStateOf(settings.accentIntensity) }
                Text("ACCENT INTENSITY · ${(accent * 100).toInt()}%", style = CaptionMono, color = mio.textSecondary)
                Slider(
                    value = accent, onValueChange = { accent = it },
                    onValueChangeFinished = { scope.launch { app.settingsRepo.setAccentIntensity(accent) } },
                    valueRange = 0.3f..1.0f,
                    colors = SliderDefaults.colors(thumbColor = mio.accent, activeTrackColor = mio.accent),
                    modifier = Modifier.semantics { contentDescription = "Accent intensity, ${(accent * 100).toInt()} percent" },
                )
                SettingRow(title = "Animation", desc = "Reduced keeps fades; Off is fully static.") {
                    Spacer(Modifier.width(1.dp))
                }
                SegmentedOptions(
                    options = listOf(
                        AnimationPref.FULL to "Full",
                        AnimationPref.REDUCED to "Reduced",
                        AnimationPref.OFF to "Off",
                    ),
                    selected = settings.animation,
                    onSelect = { scope.launch { app.settingsRepo.setAnimation(it) } },
                )
                InfoNote("Your system animator-scale accessibility setting always wins when set to off.")
                MioTextField(
                    value = nickname, onChange = { nickname = it },
                    label = "Your name", placeholder = "What Mio calls you",
                    onIme = { focus.clearFocus() },
                )
                PrimaryButton(
                    "Save name",
                    { scope.launch { app.settingsRepo.setNickname(nickname.ifBlank { null }) } },
                    Modifier.fillMaxWidth(), compact = true,
                )
                MioDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = dim.sm))

                // -------------------------------------------------- Privacy
                SectionHeader(title = "Privacy")
                SettingRow(
                    title = "Keep history",
                    desc = "Keep conversation between restarts.",
                    onToggle = { scope.launch { app.settingsRepo.setKeepHistory(!settings.keepHistory) } },
                ) {
                    MioToggle(settings.keepHistory) {
                        scope.launch { app.settingsRepo.setKeepHistory(it) }
                    }
                }
                InfoNote("On-device only: settings, your encrypted API key, and the last 40 conversation turns. Nothing is uploaded by Mio itself.")
                SecondaryButton(
                    "Clear history",
                    { vm.clearHistory() },
                    Modifier.fillMaxWidth(), destructive = true,
                )
                MioDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = dim.sm))

                // ---------------------------------------------------- About
                SectionHeader(title = "About")
                InfoNote("Mio AI v${BuildConfig.VERSION_NAME} · by Mio AI contributors")
                Row(horizontalArrangement = Arrangement.spacedBy(dim.md)) {
                    SecondaryButton("Licenses", onOpenLicenses, Modifier.weight(1f), compact = true)
                    SecondaryButton("What can Mio do?", onHelp, Modifier.weight(1f), compact = true)
                }
                Spacer(Modifier.height(dim.xl))
            }
        }
    }
}

/**
 * Cloud-brain connection readout: Offline · Connecting · Connected · Failed.
 * Text always accompanies the dot (never color-only); state changes are
 * announced to screen readers via a polite live region.
 */
@Composable
private fun ConnectionStatusRow(state: ConnState, modifier: Modifier = Modifier) {
    val mio = mioColors
    val dim = mioDimens
    val motion = mioMotion
    val (label, color, detail) = when (state) {
        is ConnState.Offline -> Triple("Offline", mio.textMuted, state.detail)
        ConnState.Connecting -> Triple("Connecting", mio.accent, "Checking your endpoint…")
        is ConnState.Connected -> Triple("Connected", mio.success, state.detail)
        is ConnState.Failed -> Triple("Failed", mio.danger, state.detail)
    }
    val dotColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(if (motion.transitions) dim.durationNormal else 0),
        label = "conn",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dim.sm),
    ) {
        if (state is ConnState.Connecting && motion.transitions) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = mio.accent,
            )
        } else {
            Box(Modifier.size(10.dp).background(dotColor, CircleShape))
        }
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = CaptionMono, color = dotColor)
            Text(detail, style = MioTypography.bodyMedium, color = mio.textSecondary)
        }
    }
}
