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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mio.ai.ui.components.AssistantStatusView
import com.mio.ai.ui.components.HudBackground
import com.mio.ai.ui.components.MioDivider
import com.mio.ai.ui.components.MioOrb
import com.mio.ai.ui.components.QuickActionGrid
import com.mio.ai.ui.components.VoiceWaveform
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.ReadoutMono
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.vm.AssistantStatus
import com.mio.ai.ui.vm.AssistantViewModel

/**
 * The heart of Mio: wordmark + status + destinations up top, the orb at
 * center, quick actions within reach, and a large mic at the bottom.
 * Calm, spacious, voice-first.
 */
@Composable
fun HomeScreen(
    vm: AssistantViewModel,
    onOpenSettings: () -> Unit,
    onOpenPermissions: (highlight: String?) -> Unit,
    onOpenConversation: () -> Unit,
    onOpenActions: () -> Unit,
    wakeSignal: Int,
) {
    val mio = mioColors
    val dim = mioDimens
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    val config = LocalConfiguration.current

    val status by vm.status.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val rms by vm.rms.collectAsStateWithLifecycle()
    val ticker by vm.ticker.collectAsStateWithLifecycle()
    val cloudLabel by vm.cloudLabel.collectAsStateWithLifecycle()
    val a11yOn by vm.a11yConnected.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.onBoot()
        vm.syncWakeService()
    }
    LaunchedEffect(wakeSignal) {
        if (wakeSignal > 0) vm.onWake()
    }

    var input by remember { mutableStateOf("") }
    fun micTap() {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        vm.onMicPress()
    }
    fun submit() {
        if (input.isBlank()) return
        vm.submitText(input)
        input = ""
        focus.clearFocus()
    }

    // Responsive orb: 58% of width, clamped for small → large phones.
    val orbSize = (config.screenWidthDp * 0.58f).coerceIn(180f, 248f).dp

    val statusDetail = when {
        status == AssistantStatus.LISTENING && partial.isNotBlank() -> "“$partial”"
        ticker != null -> ticker
        else -> cloudLabel
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
            // -- Top: wordmark + status + destinations -------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = dim.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = mio.accent,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(dim.sm))
                        Text(
                            "MIO",
                            style = MioTypography.displaySmall,
                            color = mio.textPrimary,
                        )
                    }
                    Text(
                        (if (a11yOn) "UI CONTROL ON · " else "") + cloudLabel.uppercase(),
                        style = CaptionMono,
                        color = mio.textMuted,
                    )
                }
                IconButton(onClick = onOpenConversation) {
                    Icon(Icons.Filled.ChatBubbleOutline, contentDescription = "Conversation", tint = mio.textSecondary)
                }
                IconButton(onClick = onOpenActions) {
                    Icon(Icons.Filled.Bolt, contentDescription = "Activity", tint = mio.textSecondary)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = mio.textSecondary)
                }
            }

            MioDivider(Modifier.fillMaxWidth().height(1.dp))

            // -- Orb + status + waveform ---------------------------------------
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(dim.md))
                MioOrb(
                    status = status,
                    amplitude01 = rms,
                    onTap = ::micTap,
                    size = orbSize,
                )
                Spacer(Modifier.height(dim.sm))
                AssistantStatusView(status = status, centered = true)
                if (statusDetail != null) {
                    Spacer(Modifier.height(dim.xs))
                    Text(
                        statusDetail,
                        style = ReadoutMono,
                        color = mio.textSecondary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
                VoiceWaveform(
                    level = rms,
                    status = status,
                    modifier = Modifier.padding(horizontal = 56.dp, vertical = dim.sm),
                )
            }

            // -- Quick actions ---------------------------------------------------
            QuickActionGrid(
                onVoiceCommand = { vm.submitText(it) },
                onOpenSettings = onOpenSettings,
            )

            Spacer(Modifier.weight(1f))

            // -- Input + large mic -----------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = dim.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dim.md),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type instead…", style = ReadoutMono) },
                    singleLine = true,
                    shape = RoundedCornerShape(dim.radiusXl),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = ::submit, enabled = input.isNotBlank()) {
                            Icon(Icons.Filled.Send, contentDescription = "Send", tint = mio.accent)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = mio.textPrimary,
                        unfocusedTextColor = mio.textPrimary,
                        focusedBorderColor = mio.accent,
                        unfocusedBorderColor = mio.line,
                        cursorColor = mio.accent,
                    ),
                )
                FloatingActionButton(
                    onClick = ::micTap,
                    modifier = Modifier.size(72.dp),
                    shape = CircleShape,
                    containerColor = when (status) {
                        AssistantStatus.LISTENING -> mio.danger
                        AssistantStatus.ERROR -> mio.danger
                        else -> mio.accent
                    },
                    contentColor = mio.void,
                ) {
                    Icon(
                        if (status == AssistantStatus.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (status == AssistantStatus.LISTENING) "Stop listening" else "Talk to Mio",
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Text(
                if (status == AssistantStatus.LISTENING) "LISTENING — TAP MIC TO CANCEL"
                else "TAP THE MIC · SAY “HELP”",
                style = CaptionMono,
                color = mio.textMuted,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = dim.sm),
            )
        }
    }
}
