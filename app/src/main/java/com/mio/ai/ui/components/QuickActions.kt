package com.mio.ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.MonoLabel
import com.mio.ai.ui.theme.mioColors

data class QuickAction(val label: String, val command: String, val icon: ImageVector)

val DEFAULT_QUICK_ACTIONS = listOf(
    QuickAction("Flashlight", "Turn on the flashlight", Icons.Filled.Lightbulb),
    QuickAction("Volume up", "Turn the volume up", Icons.Filled.VolumeUp),
    QuickAction("Wi-Fi", "Turn on Wi-Fi", Icons.Filled.Wifi),
    QuickAction("Bluetooth", "Turn on Bluetooth", Icons.Filled.Bluetooth),
    QuickAction("Camera", "Open the camera", Icons.Filled.CameraAlt),
    QuickAction("Timer 5m", "Set a timer for 5 minutes", Icons.Filled.Timer),
    QuickAction("Alarms", "Show my alarms", Icons.Filled.Alarm),
    QuickAction("Battery", "How much battery is left?", Icons.Filled.BatteryChargingFull),
    QuickAction("YouTube", "Open YouTube", Icons.Filled.PlayArrow),
    QuickAction("Home", "Go home", Icons.Filled.Home),
)

/** One-tap voice-command shortcuts running through the same pipeline as speech. */
@Composable
fun QuickActions(
    onAction: (command: String) -> Unit,
    modifier: Modifier = Modifier,
    actions: List<QuickAction> = DEFAULT_QUICK_ACTIONS,
) {
    val mio = mioColors
    val haptics = LocalHapticFeedback.current
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(actions) { a ->
            AssistChip(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onAction(a.command)
                },
                label = { Text(a.label.uppercase(), style = MonoLabel) },
                leadingIcon = { Icon(a.icon, contentDescription = null) },
                modifier = Modifier.height(48.dp),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = mio.glass,
                    labelColor = mio.textPrimary,
                    leadingIconContentColor = mio.primary,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, mio.hudLine),
            )
        }
    }
}
