package com.mio.ai.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mio.ai.ui.components.ActionCard
import com.mio.ai.ui.components.ActionRowMini
import com.mio.ai.ui.components.ActionTimeline
import com.mio.ai.ui.components.EmptyState
import com.mio.ai.ui.components.GlassSurface
import com.mio.ai.ui.components.HudBackground
import com.mio.ai.ui.components.MioTopBar
import com.mio.ai.ui.components.PanelAccent
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.theme.mioMotion
import com.mio.ai.ui.vm.AssistantViewModel
import com.mio.ai.ui.vm.EntryKind
import com.mio.ai.ui.vm.LoggedAction
import com.mio.ai.ui.vm.StepVisual

/**
 * Activity: the currently running action pinned on top (with STOP), then
 * the persistent log of past runs — expandable to the full step timeline.
 */
@Composable
fun ActionsScreen(
    vm: AssistantViewModel,
    onBack: () -> Unit,
) {
    val mio = mioColors
    val dim = mioDimens
    val motion = mioMotion
    val entries by vm.entries.collectAsStateWithLifecycle()
    val log by vm.actionLog.collectAsStateWithLifecycle()

    val running = entries.filter { it.kind == EntryKind.ACTION && it.running }
    var expandedId by remember { mutableStateOf<Long?>(null) }

    Box(Modifier.fillMaxSize()) {
        HudBackground()
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = dim.gutter),
        ) {
            MioTopBar(title = "Activity", onBack = onBack) {
                IconButton(onClick = { vm.clearActionLog() }) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "Clear activity",
                        tint = mio.textSecondary,
                    )
                }
            }
            if (running.isEmpty() && log.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Bolt,
                    title = "No activity yet",
                    hint = "Actions Mio runs will appear here with every step and result.",
                    modifier = Modifier.fillMaxWidth().padding(top = dim.xxl),
                )
                return@Column
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(dim.md),
            ) {
                items(running, key = { it.id }) { e ->
                    ActionCard(
                        entry = e,
                        onStop = { vm.stopExecution() },
                        onConfirm = { vm.confirmPending() },
                        onDismiss = { vm.dismissPending() },
                    )
                }
                if (running.isNotEmpty() && log.isNotEmpty()) {
                    item {
                        Text(
                            "PAST RUNS",
                            style = CaptionMono,
                            color = mio.textMuted,
                            modifier = Modifier.padding(top = dim.sm),
                        )
                    }
                }
                items(log.asReversed(), key = { it.id }) { run ->
                    PastRunCard(
                        run = run,
                        expanded = expandedId == run.id,
                        animated = motion.transitions,
                        onToggle = { expandedId = if (expandedId == run.id) null else run.id },
                    )
                }
                item { Spacer(Modifier.height(dim.xl)) }
            }
        }
    }
}

@Composable
private fun PastRunCard(run: LoggedAction, expanded: Boolean, animated: Boolean, onToggle: () -> Unit) {
    val mio = mioColors
    val dim = mioDimens
    val ok = run.steps.count { it.state == StepVisual.DONE_OK }
    val failed = run.steps.any { it.state == StepVisual.DONE_FAIL }
    GlassSurface(
        accent = when {
            run.cancelled -> null
            failed -> PanelAccent.DANGER
            run.succeeded -> PanelAccent.SUCCESS
            else -> null
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Show steps", onClick = onToggle),
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ActionRowMini(
                        title = run.summary,
                        atMillis = run.atMillis,
                        ok = ok,
                        total = run.steps.size,
                        failed = failed,
                        cancelled = run.cancelled,
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = mio.textSecondary,
                )
            }
            if (animated) {
                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Column {
                        Spacer(Modifier.height(dim.md))
                        ActionTimeline(steps = run.steps)
                    }
                }
            } else if (expanded) {
                Column {
                    Spacer(Modifier.height(dim.md))
                    ActionTimeline(steps = run.steps)
                }
            }
        }
    }
}
