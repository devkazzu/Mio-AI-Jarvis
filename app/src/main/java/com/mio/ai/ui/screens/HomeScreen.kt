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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mio.ai.ui.components.ChatList
import com.mio.ai.ui.components.HudBackground
import com.mio.ai.ui.components.HudDivider
import com.mio.ai.ui.components.MioOrb
import com.mio.ai.ui.components.QuickActions
import com.mio.ai.ui.components.StatusReadout
import com.mio.ai.ui.theme.MonoLabel
import com.mio.ai.ui.theme.MonoReadout
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.vm.AssistantStatus
import com.mio.ai.ui.vm.AssistantViewModel

/**
 * The JARVIS HUD: header → orb → waveform → quick actions → chat → input.
 * Voice-first ([MioOrb] and the mic button do the same thing), with a full
 * typed-command path for quiet rooms.
 */
@Composable
fun HomeScreen(
    vm: AssistantViewModel,
    onOpenSettings: () -> Unit,
    onOpenPermissions: (highlight: String?) -> Unit,
    wakeSignal: Int,
) {
    val mio = mioColors
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current

    val status by vm.status.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val rms by vm.rms.collectAsStateWithLifecycle()
    val ticker by vm.ticker.collectAsStateWithLifecycle()
    val cloudLabel by vm.cloudLabel.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
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

    Box(Modifier.fillMaxSize()) {
        HudBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // -- Header ------------------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("MIO", style = MioTypography.displaySmall, color = mio.textPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Canvas(Modifier.size(8.dp)) {
                            drawCircle(
                                color = if (a11yOn) mio.success else mio.textMuted,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            (if (a11yOn) "UI CONTROL ON  ·  " else "UI CONTROL OFF  ·  ") + cloudLabel.uppercase(),
                            style = MonoLabel,
                            color = mio.textMuted,
                        )
                    }
                }
                IconButton(onClick = { onOpenPermissions(null) }) {
                    Icon(Icons.Filled.Lock, contentDescription = "Permissions", tint = mio.textMuted)
                }
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = mio.textMuted)
                }
            }

            HudDivider(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp))

            // -- Orb + readout + waveform ------------------------------------------
            Column(
                Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MioOrb(
                    status = status,
                    level = rms,
                    reduceMotion = settings.reduceMotion,
                    onTap = ::micTap,
                    size = 200.dp,
                )
                Spacer(Modifier.height(6.dp))
                StatusReadout(status = status, partial = partial, ticker = ticker)
                com.mio.ai.ui.components.Waveform(
                    level = rms,
                    status = status,
                    reduceMotion = settings.reduceMotion,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }

            Spacer(Modifier.height(4.dp))
            QuickActions(onAction = { vm.submitText(it) })
            Spacer(Modifier.height(8.dp))

            // -- Chat / history ----------------------------------------------------
            ChatList(
                entries = entries,
                onFix = onOpenPermissions,
                onConfirm = { vm.confirmPending() },
                onDismiss = { vm.dismissPending() },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
            )

            // -- Input --------------------------------------------------------------
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Type a command…", style = MonoReadout) },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { submit() }),
                    trailingIcon = {
                        IconButton(onClick = ::submit, enabled = input.isNotBlank()) {
                            Icon(Icons.Filled.Send, contentDescription = "Send", tint = mio.primary)
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = mio.textPrimary,
                        unfocusedTextColor = mio.textPrimary,
                        focusedBorderColor = mio.primary,
                        unfocusedBorderColor = mio.hudLine,
                        cursorColor = mio.primary,
                    ),
                )
                FloatingActionButton(
                    onClick = ::micTap,
                    modifier = Modifier.size(56.dp),
                    shape = CircleShape,
                    containerColor = when (status) {
                        AssistantStatus.LISTENING -> mio.danger
                        AssistantStatus.ERROR -> mio.danger
                        else -> mio.primary
                    },
                    contentColor = androidx.compose.ui.graphics.Color(0xFF04222A),
                ) {
                    Icon(
                        if (status == AssistantStatus.LISTENING) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (status == AssistantStatus.LISTENING) "Stop listening" else "Talk to Mio",
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            Text(
                if (status == AssistantStatus.LISTENING) "LISTENING — TAP MIC TO CANCEL"
                else "TAP THE ORB OR MIC · SAY \"HELP\"",
                style = MonoLabel,
                color = mio.textMuted,
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
            )
        }
    }
}
