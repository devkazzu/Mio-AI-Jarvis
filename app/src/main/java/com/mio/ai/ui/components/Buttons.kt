package com.mio.ai.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens
import com.mio.ai.ui.theme.mioMotion

/**
 * Primary action: filled accent, 52dp minimum target (grows with font scale),
 * loading spinner (loading → progress → success handled by callers).
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    compact: Boolean = false,
    icon: ImageVector? = null,
) {
    val mio = mioColors
    val dim = mioDimens
    val motion = mioMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && motion.transitions) 0.97f else 1f,
        animationSpec = tween(dim.durationFast),
        label = "press",
    )
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(dim.radiusMd),
        contentPadding = PaddingValues(horizontal = dim.lg),
        colors = ButtonDefaults.buttonColors(
            containerColor = mio.accent,
            contentColor = mio.void,
            disabledContainerColor = mio.surfaceHigh,
            disabledContentColor = mio.textMuted,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = mio.void,
            )
            Spacer(Modifier.width(dim.sm))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(dim.sm))
        }
        Text(text.uppercase(), style = MioTypography.labelLarge)
    }
}

/**
 * Secondary action: outlined, same 52dp-minimum rhythm. [destructive] tints red
 * (Stop / Clear / Delete).
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    compact: Boolean = false,
    icon: ImageVector? = null,
) {
    val mio = mioColors
    val dim = mioDimens
    val motion = mioMotion
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && motion.transitions) 0.97f else 1f,
        animationSpec = tween(dim.durationFast),
        label = "press",
    )
    val tint = if (destructive) mio.danger else mio.accent
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = modifier
            .heightIn(min = 52.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale),
        shape = RoundedCornerShape(dim.radiusMd),
        contentPadding = PaddingValues(horizontal = dim.lg),
        border = androidx.compose.foundation.BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(dim.sm))
        }
        Text(text.uppercase(), style = MioTypography.labelLarge)
    }
}

/** Full-width convenience row for Confirm / Cancel pairs. */
@Composable
fun ConfirmRow(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(mioDimens.md),
    ) {
        PrimaryButton(confirmText, onConfirm, Modifier.weight(1f))
        SecondaryButton(dismissText, onDismiss, Modifier.weight(1f))
    }
}
