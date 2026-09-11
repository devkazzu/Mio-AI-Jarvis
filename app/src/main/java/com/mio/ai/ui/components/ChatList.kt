package com.mio.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mio.ai.ui.theme.MonoLabel
import com.mio.ai.ui.theme.MonoReadout
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.vm.ChatEntry
import com.mio.ai.ui.vm.EntryKind
import com.mio.ai.ui.vm.StepVisual
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Conversation + action history. Action cards embed the live per-step
 * checklist so "what Mio is doing" is always visible.
 */
@Composable
fun ChatList(
    entries: List<ChatEntry>,
    onFix: (destination: String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mio = mioColors
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }
    if (entries.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "SAY \"HELP\" TO SEE WHAT I CAN DO",
                style = MonoLabel,
                color = mio.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(entries, key = { it.id }) { e ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 3 }),
            ) {
                when (e.kind) {
                    EntryKind.USER -> UserBubble(e)
                    EntryKind.ASSISTANT -> AssistantBubble(e)
                    EntryKind.ACTION -> ActionCard(e, onConfirm, onDismiss)
                    EntryKind.SYSTEM -> SystemLine(e, onFix)
                }
            }
        }
    }
}

@Composable
private fun UserBubble(e: ChatEntry) {
    val mio = mioColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            color = mio.userBubble,
            border = androidx.compose.foundation.BorderStroke(1.dp, mio.hudLine),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(e.text, style = MonoReadout, color = mio.textPrimary)
                Timestamp(e.atMillis, Alignment.End)
            }
        }
    }
}

@Composable
private fun AssistantBubble(e: ChatEntry) {
    val mio = mioColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            color = mio.glass,
            border = androidx.compose.foundation.BorderStroke(1.dp, mio.hudLine),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    "MIO",
                    style = MonoLabel,
                    color = mio.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(e.text, color = mio.textPrimary, fontSize = 15.sp, lineHeight = 22.sp)
                Timestamp(e.atMillis, Alignment.Start)
            }
        }
    }
}

@Composable
private fun ActionCard(e: ChatEntry, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val mio = mioColors
    HudPanel(
        modifier = Modifier.fillMaxWidth(),
        bracketColor = when {
            e.awaitingConfirm -> mio.warning
            e.steps.any { it.state == StepVisual.DONE_FAIL } -> mio.danger
            e.steps.all { it.state == StepVisual.DONE_OK } && e.steps.isNotEmpty() -> mio.success
            else -> mio.executing
        },
    ) {
        Column {
            Text("ACTION SEQUENCE", style = MonoLabel, color = mio.textMuted)
            Spacer(Modifier.height(4.dp))
            Text(e.text, color = mio.textPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(8.dp))
            e.steps.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (s.state) {
                        StepVisual.PENDING -> PendingDot()
                        StepVisual.RUNNING -> CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = mio.executing,
                        )
                        StepVisual.DONE_OK -> Icon(
                            Icons.Filled.Check, contentDescription = "Done",
                            tint = mio.success, modifier = Modifier.size(16.dp),
                        )
                        StepVisual.DONE_FAIL -> Icon(
                            Icons.Filled.Close, contentDescription = "Failed",
                            tint = mio.danger, modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            s.label,
                            fontSize = 13.sp,
                            color = if (s.state == StepVisual.PENDING) mio.textMuted else mio.textPrimary,
                        )
                        if (s.detail != null && s.state != StepVisual.PENDING) {
                            Text(s.detail, style = MonoLabel, color = mio.textMuted)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            if (e.awaitingConfirm) {
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = mio.primary,
                            contentColor = androidx.compose.ui.graphics.Color(0xFF04222A),
                        ),
                    ) { Text("CONFIRM", style = MonoLabel) }
                    OutlinedButton(onClick = onDismiss) { Text("CANCEL", style = MonoLabel) }
                }
            }
        }
    }
}

@Composable
private fun PendingDot() {
    val mio = mioColors
    androidx.compose.foundation.Canvas(Modifier.size(16.dp)) {
        drawCircle(color = mio.textMuted.copy(alpha = 0.55f), radius = 5f)
    }
}

@Composable
private fun SystemLine(e: ChatEntry, onFix: (String) -> Unit) {
    val mio = mioColors
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            e.text,
            style = MonoLabel,
            color = mio.warning,
            textAlign = TextAlign.Center,
        )
        if (e.fixDestination != null) {
            TextButton(onClick = { onFix(e.fixDestination) }) {
                Text("TAP TO FIX →", style = MonoLabel, color = mio.primary)
            }
        }
    }
}

@Composable
private fun Timestamp(atMillis: Long, alignment: Alignment.Horizontal) {
    val mio = mioColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (alignment == Alignment.End) Arrangement.End else Arrangement.Start) {
        Text(
            SimpleDateFormat("HH:mm", Locale.US).format(Date(atMillis)),
            style = MonoLabel,
            color = mio.textMuted.copy(alpha = 0.7f),
        )
    }
}
