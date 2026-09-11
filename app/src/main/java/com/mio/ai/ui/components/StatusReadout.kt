package com.mio.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.MonoLabel
import com.mio.ai.ui.theme.MonoReadout
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.vm.AssistantStatus

/**
 * HUD status block under the orb: STATUS // <state>, live transcript while
 * listening, and the current action ticker while executing.
 */
@Composable
fun StatusReadout(
    status: AssistantStatus,
    partial: String,
    ticker: String?,
    modifier: Modifier = Modifier,
) {
    val mio = mioColors
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "STATUS // ${status.name}",
            style = MonoLabel,
            color = statusColor(status),
        )
        AnimatedVisibility(
            visible = status == AssistantStatus.LISTENING && partial.isNotBlank(),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "“$partial”",
                    style = MonoReadout,
                    color = mio.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        AnimatedVisibility(
            visible = ticker != null,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "▸ ${ticker.orEmpty()}",
                    style = MonoReadout,
                    color = mio.executing,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
