package com.mio.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens

/**
 * Never a bare spinner: every wait carries a contextual label.
 */
@Composable
fun LoadingState(label: String, modifier: Modifier = Modifier) {
    val mio = mioColors
    val dim = mioDimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dim.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dim.sm),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.dp,
            color = mio.accent,
        )
        Text(label, style = CaptionMono, color = mio.textSecondary, textAlign = TextAlign.Center)
    }
}

/**
 * Every error carries a recovery action.
 */
@Composable
fun ErrorState(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mio = mioColors
    val dim = mioDimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dim.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dim.sm),
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = mio.danger, modifier = Modifier.size(30.dp))
        Text(message, style = MioTypography.bodyMedium, color = mio.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(dim.xs))
        SecondaryButton(actionLabel, onAction)
    }
}

/**
 * Friendly empty lists — always with a next step hint.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val mio = mioColors
    val dim = mioDimens
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(dim.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(dim.sm),
    ) {
        Icon(icon, contentDescription = null, tint = mio.textMuted, modifier = Modifier.size(34.dp))
        Text(title, style = MioTypography.titleLarge, color = mio.textPrimary, textAlign = TextAlign.Center)
        Text(hint, style = MioTypography.bodyMedium, color = mio.textSecondary, textAlign = TextAlign.Center)
    }
}

/** Small inline note (permission hints, footnotes). */
@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    val mio = mioColors
    val dim = mioDimens
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dim.sm),
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = mio.textMuted, modifier = Modifier.size(16.dp))
        Text(text, style = MioTypography.bodyMedium, color = mio.textSecondary)
    }
}
