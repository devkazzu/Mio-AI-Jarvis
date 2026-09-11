package com.mio.ai.ui.screens

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mio.ai.BuildConfig
import com.mio.ai.MioApplication
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
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.vm.AssistantViewModel
import kotlinx.coroutines.launch

/**
 * Polished settings: AI · Voice · Automation · Appearance · Privacy · About.
 * Every control writes through to DataStore (and encrypted storage for keys).
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
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cloudLabel by vm.cloudLabel.collectAsStateWithLifecycle()
    val voices by vm.ttsVoices.collectAsStateWithLifecycle()
    val a11yOn by vm.a11yConnected.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshVoices() }

    var url by remember(settings.aiBaseUrlOverride) { mutableStateOf(settings.aiBaseUrlOverride) }
    var model by remember(settings.aiModelOverride) { mutableStateOf(settings.aiModelOverride) }
    var apiKey by remember { mutableStateOf("") }
    var keyTick by remember { mutableIntStateOf(0) }
    var nickname by remember(settings.nickname) { mutableStateOf(settings.nickname.orEmpty()) }
    val keyStored = remember(keyTick) { app.secureKeys.hasApiKey() }

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
                MioTextField(value = url, onChange = { url = it }, label = "Base URL", placeholder = "https://api.openai.com/v1")
                MioTextField(value = model, onChange = { model = it }, label = "Model", placeholder = "gpt-4o-mini")
                MioTextField(
                    value = apiKey, onChange = { apiKey = it },
                    label = "API key ${if (keyStored) "(stored)" else "(not set)"}",
                    placeholder = "sk-…", password = true, keyboard = KeyboardType.Password,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(dim.md)) {
                    PrimaryButton("Save", { scope.launch { app.settingsRepo.setAiEndpoint(url, model) } }, Modifier.weight(1f), compact = true)
                    SecondaryButton(
                        if (apiKey.isNotBlank()) "Set key" else "Clear key",
                        {
                            scope.launch {
                                if (apiKey.isNotBlank()) app.secureKeys.setApiKey(apiKey) else app.secureKeys.clearApiKey()
                                apiKey = ""
                                keyTick++
                            }
                        },
                        Modifier.weight(1f), compact = true,
                    )
                }
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
                )
                var pitch by remember(settings.speechPitch) { mutableFloatStateOf(settings.speechPitch) }
                Text("PITCH · ${"%.2f".format(pitch)}×", style = CaptionMono, color = mio.textSecondary)
                Slider(
                    value = pitch, onValueChange = { pitch = it },
                    onValueChangeFinished = { scope.launch { app.settingsRepo.setSpeechPitch(pitch) } },
                    valueRange = 0.5f..2.0f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = mio.accent, activeTrackColor = mio.accent),
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
                MioTextField(value = nickname, onChange = { nickname = it }, label = "Your name", placeholder = "What Mio calls you")
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
