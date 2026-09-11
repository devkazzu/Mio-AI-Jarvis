package com.mio.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Conversation bubble. User right / Mio left. Mio messages carry a replay
 * affordance (voice playback) and a timestamp caption.
 */
@Composable
fun ChatBubble(
    text: String,
    isUser: Boolean,
    atMillis: Long,
    modifier: Modifier = Modifier,
    onReplay: (() -> Unit)? = null,
) {
    val mio = mioColors
    val dim = mioDimens
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(
                topStart = dim.radiusBubble,
                topEnd = dim.radiusBubble,
                bottomStart = if (isUser) dim.radiusBubble else dim.radiusTail,
                bottomEnd = if (isUser) dim.radiusTail else dim.radiusBubble,
            ),
            color = if (isUser) mio.surfaceHigh else mio.surface,
            border = BorderStroke(1.dp, mio.line),
        ) {
            Column(Modifier.padding(horizontal = dim.lg, vertical = dim.md)) {
                if (!isUser) {
                    Text("MIO", style = CaptionMono, color = mio.accent)
                    Spacer(Modifier.height(dim.xs))
                }
                Text(
                    text,
                    style = MioTypography.bodyLarge,
                    color = mio.textPrimary,
                )
                Spacer(Modifier.height(dim.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.US).format(Date(atMillis)),
                        style = CaptionMono,
                        color = mio.textMuted,
                    )
                    // Full 48dp touch target (Material default) — never shrunk.
                    if (!isUser && onReplay != null) {
                        IconButton(onClick = onReplay) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Replay message",
                                tint = mio.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
