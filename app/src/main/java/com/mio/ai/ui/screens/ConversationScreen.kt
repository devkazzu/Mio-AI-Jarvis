package com.mio.ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mio.ai.ui.components.ConversationList
import com.mio.ai.ui.components.HudBackground
import com.mio.ai.ui.components.MioTopBar
import com.mio.ai.ui.theme.ReadoutMono
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.vm.AssistantViewModel

/**
 * Full conversation history: bubbles with voice replay, inline live action
 * cards, system lines with recovery — plus a composer.
 */
@Composable
fun ConversationScreen(
    vm: AssistantViewModel,
    onBack: () -> Unit,
    onFix: (destination: String) -> Unit,
) {
    val mio = mioColors
    val dim = mioDimens
    val focus = LocalFocusManager.current
    val entries by vm.entries.collectAsStateWithLifecycle()

    var input by remember { mutableStateOf("") }
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
                .navigationBarsPadding()
                .padding(horizontal = dim.gutter),
        ) {
            MioTopBar(title = "Chat", onBack = onBack) {
                IconButton(onClick = { vm.clearHistory() }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Clear conversation",
                        tint = mio.textSecondary,
                    )
                }
            }
            ConversationList(
                entries = entries,
                onReplay = { vm.speakMessage(it) },
                onFix = onFix,
                onConfirm = { vm.confirmPending() },
                onDismiss = { vm.dismissPending() },
                onStop = { vm.stopExecution() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = dim.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dim.sm),
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Mio…", style = ReadoutMono) },
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
            }
            androidx.compose.foundation.layout.Spacer(
                Modifier.align(Alignment.CenterHorizontally).padding(dim.xs),
            )
        }
    }
}
