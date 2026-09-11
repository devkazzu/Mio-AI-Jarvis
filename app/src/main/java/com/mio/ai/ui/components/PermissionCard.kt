package com.mio.ai.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens

/**
 * One permission row: status icon, title, plain-language "why", and a
 * compact CTA when action is needed. Highlighted when an action just failed
 * for this permission.
 */
@Composable
fun PermissionCard(
    title: String,
    why: String,
    granted: Boolean,
    modifier: Modifier = Modifier,
    infoOnly: Boolean = false,
    ctaLabel: String = "Allow",
    highlighted: Boolean = false,
    onCta: (() -> Unit)? = null,
) {
    val mio = mioColors
    val dim = mioDimens
    GlassSurface(
        modifier = modifier.fillMaxWidth(),
        accent = if (highlighted && !granted) PanelAccent.WARNING else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    infoOnly -> Icons.Filled.Info
                    granted -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.RadioButtonUnchecked
                },
                contentDescription = if (granted) "Granted" else "Not granted",
                tint = when {
                    infoOnly -> mio.textMuted
                    granted -> mio.success
                    else -> mio.textSecondary
                },
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(dim.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MioTypography.bodyLarge, color = mio.textPrimary)
                    if (highlighted && !granted) {
                        Spacer(Modifier.width(dim.sm))
                        Text("NEEDED NOW", style = CaptionMono, color = mio.warning)
                    }
                }
                Spacer(Modifier.height(dim.xs))
                Text(why, style = MioTypography.bodyMedium, color = mio.textSecondary)
            }
        }
        if (!granted && !infoOnly && onCta != null) {
            Spacer(Modifier.height(dim.md))
            SecondaryButton(ctaLabel, onCta, modifier = Modifier.fillMaxWidth(), compact = true)
        }
    }
}
