package com.mio.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens

private data class QuickAction(
    val label: String,
    val icon: ImageVector,
    val command: String?,
)

/**
 * Home quick actions in a calm 3 + 2 grid: Camera, Flashlight, Alarm, Apps,
 * Settings. Voice commands run the normal pipeline; Settings navigates.
 */
@Composable
fun QuickActionGrid(
    onVoiceCommand: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actions = listOf(
        QuickAction("Camera", Icons.Filled.CameraAlt, "Open the camera"),
        QuickAction("Flashlight", Icons.Filled.Lightbulb, "Toggle the flashlight"),
        QuickAction("Alarm", Icons.Filled.Alarm, "Show my alarms"),
        QuickAction("Apps", Icons.Filled.Apps, "Show my apps"),
        QuickAction("Settings", Icons.Filled.Settings, null),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(mioDimens.md)) {
        Row(horizontalArrangement = Arrangement.spacedBy(mioDimens.md), modifier = Modifier.fillMaxWidth()) {
            actions.take(3).forEach {
                QuickCell(it, Modifier.weight(1f), onVoiceCommand, onOpenSettings)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(mioDimens.md), modifier = Modifier.fillMaxWidth()) {
            actions.drop(3).forEach {
                QuickCell(it, Modifier.weight(1f), onVoiceCommand, onOpenSettings)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun QuickCell(
    action: QuickAction,
    modifier: Modifier,
    onVoiceCommand: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val mio = mioColors
    val dim = mioDimens
    val haptics = LocalHapticFeedback.current
    Surface(
        modifier = modifier
            .height(84.dp)
            .clickable(role = Role.Button, onClickLabel = action.label) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (action.command != null) onVoiceCommand(action.command) else onOpenSettings()
            },
        shape = RoundedCornerShape(dim.radiusMd),
        color = mio.surface,
        border = BorderStroke(1.dp, mio.line),
    ) {
        Column(
            modifier = Modifier.padding(dim.sm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(action.icon, contentDescription = null, tint = mio.accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(dim.xs))
            Text(
                action.label.uppercase(),
                style = CaptionMono,
                color = mio.textSecondary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
