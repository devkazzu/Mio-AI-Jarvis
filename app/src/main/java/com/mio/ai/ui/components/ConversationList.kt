package com.mio.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.theme.mioMotion
import com.mio.ai.ui.vm.ChatEntry
import com.mio.ai.ui.vm.EntryKind

/**
 * Shared conversation renderer (Conversation screen + anywhere history
 * appears): user / Mio bubbles with replay, live action cards, and system
 * lines with recovery actions.
 */
@Composable
fun ConversationList(
    entries: List<ChatEntry>,
    onReplay: (String) -> Unit,
    onFix: (destination: String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mio = mioColors
    val dim = mioDimens
    val motion = mioMotion
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) {
            if (motion.transitions) listState.animateScrollToItem(entries.lastIndex)
            else listState.scrollToItem(entries.lastIndex)
        }
    }
    if (entries.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.AutoAwesome,
            title = "Nothing here yet",
            hint = "Tap the mic and say hello — or try “what can you do”.",
            modifier = modifier,
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(vertical = dim.sm),
        verticalArrangement = Arrangement.spacedBy(dim.md),
    ) {
        items(entries, key = { it.id }) { e ->
            if (motion.transitions) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
                ) {
                    EntryContent(e, onReplay, onFix, onConfirm, onDismiss, onStop)
                }
            } else {
                EntryContent(e, onReplay, onFix, onConfirm, onDismiss, onStop)
            }
        }
    }
}

@Composable
private fun EntryContent(
    e: ChatEntry,
    onReplay: (String) -> Unit,
    onFix: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    onStop: () -> Unit,
) {
    when (e.kind) {
        EntryKind.USER -> ChatBubble(text = e.text, isUser = true, atMillis = e.atMillis)
        EntryKind.ASSISTANT -> ChatBubble(
            text = e.text,
            isUser = false,
            atMillis = e.atMillis,
            onReplay = { onReplay(e.text) },
        )
        EntryKind.ACTION -> ActionCard(
            entry = e,
            onStop = onStop,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        EntryKind.SYSTEM -> SystemLine(text = e.text, fixDestination = e.fixDestination, onFix = onFix)
    }
}

@Composable
private fun SystemLine(text: String, fixDestination: String?, onFix: (String) -> Unit) {
    val mio = mioColors
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text,
            style = MioTypography.bodyMedium,
            color = mio.warning,
            textAlign = TextAlign.Center,
        )
        if (fixDestination != null) {
            TextButton(onClick = { onFix(fixDestination) }) {
                Text("TAP TO FIX", style = CaptionMono, color = mio.accent)
            }
        }
    }
}
