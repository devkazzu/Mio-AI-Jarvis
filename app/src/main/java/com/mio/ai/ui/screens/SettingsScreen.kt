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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mio.ai.BuildConfig
import com.mio.ai.MioApplication
import com.mio.ai.ui.components.HudBackground
import com.mio.ai.ui.components.HudDivider
import com.mio.ai.ui.theme.MonoLabel
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.vm.AssistantViewModel
import kotlinx.coroutines.launch

/**
 * Settings: AI brain credentials (encrypted), voice tuning, behavior,
 * history and about. Keys never touch source control.
 */
@Composable
fun SettingsScreen(
    vm: AssistantViewModel,
    onBack: () -> Unit,
    onOpenPermissions: () -> Unit,
    onHelp: () -> Unit,
) {
    val mio = mioColors
    val ctx = LocalContext.current
    val app = remember(ctx) { ctx.applicationContext as MioApplication }
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cloudLabel by vm.cloudLabel.collectAsStateWithLifecycle()

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
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = mio.textPrimary)
                }
                Text("SETTINGS", style = MioTypography.titleMedium, color = mio.textPrimary)
            }
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // ------------------------------------------------ AI brain
                SectionTitle("AI BRAIN")
                Text(cloudLabel.uppercase(), style = MonoLabel, color = mio.primary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Any OpenAI-compatible endpoint (OpenAI, Ollama, LM Studio, OpenRouter…). " +
                        "Leave empty to stay fully offline — commands still work.",
                    color = mio.textMuted, fontSize = 12.sp, lineHeight = 17.sp,
                )
                SwitchRow(
                    title = "Use cloud brain",
                    desc = "Off = 100% offline, on-device only.",
                    checked = settings.useCloudAi,
                    onChange = { scope.launch { app.settingsRepo.setUseCloudAi(it) } },
                )
                MioField(value = url, onChange = { url = it }, label = "Base URL", placeholder = "https://api.openai.com/v1")
                MioField(value = model, onChange = { model = it }, label = "Model", placeholder = "gpt-4o-mini")
                MioField(
                    value = apiKey, onChange = { apiKey = it },
                    label = "API key ${if (keyStored) "(stored ✓)" else "(not set)"}",
                    placeholder = "sk-…",
                    password = true,
                    keyboard = KeyboardType.Password,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MioButton("SAVE AI CONFIG", Modifier.weight(1f)) {
                        scope.launch {
                            app.settingsRepo.setAiEndpoint(url, model)
                            if (apiKey.isNotBlank()) app.secureKeys.setApiKey(apiKey)
                            apiKey = ""
                            keyTick++
                        }
                    }
                    OutlinedButton(
                        onClick = { scope.launch { app.secureKeys.clearApiKey(); keyTick++ } },
                        enabled = keyStored,
                    ) { Text("CLEAR KEY", style = MonoLabel) }
                }
                HudDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 10.dp))

                // --------------------------------------------------- voice
                SectionTitle("VOICE")
                SwitchRow(
                    title = "Spoken replies",
                    desc = "Mio talks back. Off = text only.",
                    checked = settings.voiceReplies,
                    onChange = { scope.launch { app.settingsRepo.setVoiceReplies(it) } },
                )
                var rate by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
                Text("SPEECH RATE · ${"%.2f".format(rate)}×", style = MonoLabel, color = mio.textMuted)
                Slider(
                    value = rate, onValueChange = { rate = it },
                    onValueChangeFinished = { scope.launch { app.settingsRepo.setSpeechRate(rate) } },
                    valueRange = 0.5f..2.0f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = mio.primary, activeTrackColor = mio.primary),
                )
                var pitch by remember(settings.speechPitch) { mutableFloatStateOf(settings.speechPitch) }
                Text("PITCH · ${"%.2f".format(pitch)}×", style = MonoLabel, color = mio.textMuted)
                Slider(
                    value = pitch, onValueChange = { pitch = it },
                    onValueChangeFinished = { scope.launch { app.settingsRepo.setSpeechPitch(pitch) } },
                    valueRange = 0.5f..2.0f, steps = 5,
                    colors = SliderDefaults.colors(thumbColor = mio.primary, activeTrackColor = mio.primary),
                )
                HudDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 10.dp))

                // ------------------------------------------------ behavior
                SectionTitle("BEHAVIOR")
                SwitchRow(
                    title = "Confirm before actions",
                    desc = "Mio asks before calls, texts and multi-step automation.",
                    checked = settings.confirmations,
                    onChange = { scope.launch { app.settingsRepo.setConfirmations(it) } },
                )
                SwitchRow(
                    title = "Wake word “Hey Mio”",
                    desc = "Always-on listening (uses more battery). Needs mic + notifications.",
                    checked = settings.wakeWord,
                    onChange = { vm.setWakeWord(it) },
                )
                SwitchRow(
                    title = "Reduce motion",
                    desc = "Static orb, no pulse or waveform animation.",
                    checked = settings.reduceMotion,
                    onChange = { scope.launch { app.settingsRepo.setReduceMotion(it) } },
                )
                MioField(value = nickname, onChange = { nickname = it }, label = "Your name", placeholder = "Mio will call you this")
                MioButton("SAVE NAME", Modifier.fillMaxWidth()) {
                    scope.launch { app.settingsRepo.setNickname(nickname.ifBlank { null }) }
                }
                OutlinedButton(onClick = onOpenPermissions, modifier = Modifier.fillMaxWidth()) {
                    Text("OPEN PERMISSIONS", style = MonoLabel)
                }
                HudDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 10.dp))

                // ---------------------------------------------------- data
                SectionTitle("HISTORY")
                OutlinedButton(
                    onClick = { vm.clearHistory() },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("CLEAR CONVERSATION", style = MonoLabel) }
                HudDivider(Modifier.fillMaxWidth().height(1.dp).padding(vertical = 10.dp))

                // --------------------------------------------------- about
                SectionTitle("ABOUT")
                Text(
                    "Mio AI v${BuildConfig.VERSION_NAME} — a JARVIS-style voice assistant that " +
                        "actually operates your phone. Say “help” any time to hear what I can do.",
                    color = mio.textMuted, fontSize = 12.sp, lineHeight = 17.sp,
                )
                Spacer(Modifier.height(8.dp))
                MioButton("HEAR WHAT MIO CAN DO", Modifier.fillMaxWidth(), onClick = onHelp)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MioTypography.titleMedium, color = mioColors.textPrimary)
}

@Composable
private fun SwitchRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val mio = mioColors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = mio.textPrimary, fontSize = 14.sp)
            Text(desc, color = mio.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = mio.primary,
                checkedTrackColor = mio.primary.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
private fun MioField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String,
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    val mio = mioColors
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label.uppercase(), style = MonoLabel, color = mio.textMuted)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = mio.textMuted.copy(alpha = 0.6f), fontSize = 13.sp) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = mio.textPrimary,
                unfocusedTextColor = mio.textPrimary,
                focusedBorderColor = mio.primary,
                unfocusedBorderColor = mio.hudLine,
                cursorColor = mio.primary,
            ),
        )
    }
}

@Composable
private fun MioButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val mio = mioColors
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = mio.primary,
            contentColor = Color(0xFF04222A),
        ),
    ) { Text(text, style = MonoLabel) }
}
