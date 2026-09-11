package com.mio.ai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.vm.ChatEntry
import com.mio.ai.ui.vm.StepVisual
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Live execution panel ("MIO IS WORKING"): header state, summary, real-time
 * [ActionTimeline], and Stop / Confirm controls. Used inline in Conversation
 * and pinned in Actions.
 */
@Composable
fun ActionCard(
    entry: ChatEntry,
    onStop: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    showStop: Boolean = true,
) {
    val mio = mioColors
    val dim = mioDimens
    val failed = entry.steps.any { it.state == StepVisual.DONE_FAIL }
    val allDone = entry.steps.isNotEmpty() && entry.steps.all { it.state == StepVisual.DONE_OK }

    val accent = when {
        entry.awaitingConfirm -> PanelAccent.WARNING
        entry.running -> PanelAccent.CYAN
        entry.cancelled -> null
        failed -> PanelAccent.DANGER
        allDone -> PanelAccent.SUCCESS
        else -> null
    }
    val header = when {
        entry.awaitingConfirm -> "CONFIRM ACTION"
        entry.running -> "MIO IS WORKING"
        entry.cancelled -> "STOPPED"
        failed -> "ACTION NEEDS ATTENTION"
        allDone -> "ACTION COMPLETE"
        else -> "ACTION"
    }
    val headerColor = when {
        entry.awaitingConfirm -> mio.warning
        entry.running -> mio.accent
        entry.cancelled -> mio.textMuted
        failed -> mio.danger
        allDone -> mio.success
        else -> mio.textSecondary
    }

    GlassSurface(modifier = modifier.fillMaxWidth(), accent = accent) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(header, style = CaptionMono, color = headerColor, modifier = Modifier.weight(1f))
                Text(
                    SimpleDateFormat("HH:mm", Locale.US).format(Date(entry.atMillis)),
                    style = CaptionMono,
                    color = mio.textMuted,
                )
            }
            Spacer(Modifier.height(dim.xs))
            Text(entry.text, style = MioTypography.titleLarge, color = mio.textPrimary)
            Spacer(Modifier.height(dim.md))
            ActionTimeline(steps = entry.steps)
            if (entry.awaitingConfirm) {
                Spacer(Modifier.height(dim.md))
                ConfirmRow(onConfirm = onConfirm, onDismiss = onDismiss)
            } else if (entry.running && showStop) {
                Spacer(Modifier.height(dim.md))
                SecondaryButton(
                    text = "Stop",
                    onClick = onStop,
                    destructive = true,
                    icon = Icons.Filled.Stop,
                    modifier = Modifier.fillMaxWidth(),
                    compact = true,
                )
            }
            if (entry.fixDestination != null && failed) {
                Spacer(Modifier.height(dim.xs))
                Text(
                    "A missing permission blocked this step.",
                    style = CaptionMono,
                    color = mio.textSecondary,
                )
            }
        }
    }
}

/** Compact horizontal action row (used in the Actions log when collapsed). */
@Composable
fun ActionRowMini(
    title: String,
    atMillis: Long,
    ok: Int,
    total: Int,
    failed: Boolean,
    cancelled: Boolean,
    modifier: Modifier = Modifier,
) {
    val mio = mioColors
    val dim = mioDimens
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MioTypography.bodyLarge, color = mio.textPrimary, maxLines = 1)
            Spacer(Modifier.height(dim.xs))
            StepSummaryLine(ok = ok, total = total, failed = failed, cancelled = cancelled)
        }
        Spacer(Modifier.width(dim.sm))
        Text(
            SimpleDateFormat("HH:mm", Locale.US).format(Date(atMillis)),
            style = CaptionMono,
            color = mio.textMuted,
        )
    }
}
