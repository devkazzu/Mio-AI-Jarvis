package com.mio.ai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.mio.ai.ui.theme.CaptionMono
import com.mio.ai.ui.theme.MioTypography
import com.mio.ai.ui.theme.mioColors
import com.mio.ai.ui.theme.mioDimens

/**
 * Standard settings row: title + description + trailing control.
 * Minimum 64dp tall; the whole row toggles when [onToggle] is set.
 */
@Composable
fun SettingRow(
    title: String,
    desc: String,
    modifier: Modifier = Modifier,
    onToggle: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val mio = mioColors
    val dim = mioDimens
    val clickableMod = if (onToggle != null) {
        modifier.clickable(role = Role.Switch, onClick = onToggle)
    } else {
        modifier
    }
    Row(
        clickableMod
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(vertical = dim.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MioTypography.bodyLarge, color = mio.textPrimary)
            Text(desc, style = MioTypography.bodyMedium, color = mio.textSecondary)
        }
        Spacer(Modifier.width(dim.md))
        trailing()
    }
}

/** Styled toggle with a guaranteed 48dp touch target. */
@Composable
fun MioToggle(checked: Boolean, modifier: Modifier = Modifier, onChange: (Boolean) -> Unit) {
    val mio = mioColors
    Box(modifier.height(48.dp), contentAlignment = Alignment.Center) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = mio.accent,
                checkedTrackColor = mio.accent.copy(alpha = 0.35f),
                checkedBorderColor = mio.accent,
                uncheckedThumbColor = mio.textSecondary,
                uncheckedTrackColor = mio.surfaceHigh,
            ),
        )
    }
}

/** Segmented single-choice options (theme, style, mode…). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedOptions(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val mio = mioColors
    val dim = mioDimens
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, label) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = mio.accent.copy(alpha = 0.18f),
                    activeContentColor = mio.accent,
                    inactiveContentColor = mio.textSecondary,
                ),
            ) {
                Text(label.uppercase(), style = MioTypography.labelLarge)
            }
        }
    }
    Spacer(Modifier.height(dim.xs))
}

/** Minus/plus stepper for numeric settings (timeout, memory depth). */
@Composable
fun StepperRow(
    valueText: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusEnabled: Boolean = true,
    plusEnabled: Boolean = true,
) {
    val mio = mioColors
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(mioDimens.xs),
    ) {
        IconButton(onClick = onMinus, enabled = minusEnabled) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease", tint = mio.textPrimary)
        }
        Text(
            valueText,
            style = MioTypography.bodyLarge,
            color = mio.textPrimary,
            modifier = Modifier.widthIn(min = 56.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = onPlus, enabled = plusEnabled) {
            Icon(Icons.Filled.Add, contentDescription = "Increase", tint = mio.textPrimary)
        }
    }
}

/**
 * Labeled text field used across Settings.
 * [error] shows an inline message (danger) and error border; [trailingIcon]
 * hosts affordances like the password show/hide toggle; [imeAction]/[onIme]
 * drive keyboard Next/Done behavior for fast, accessible form flow.
 */
@Composable
fun MioTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    password: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onIme: () -> Unit = {},
    error: String? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    val mio = mioColors
    val dim = mioDimens
    Column(modifier.fillMaxWidth().padding(vertical = dim.xs)) {
        Text(label.uppercase(), style = CaptionMono, color = mio.textSecondary)
        Spacer(Modifier.height(dim.xs))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, style = MioTypography.bodyMedium, color = mio.textMuted) },
            singleLine = true,
            isError = error != null,
            trailingIcon = trailingIcon,
            supportingText = error?.let { msg ->
                { Text(msg, style = MioTypography.bodyMedium, color = mio.danger) }
            },
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { onIme() },
                onDone = { onIme() },
                onGo = { onIme() },
                onSearch = { onIme() },
                onSend = { onIme() },
            ),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            textStyle = MioTypography.bodyLarge,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = mio.textPrimary,
                unfocusedTextColor = mio.textPrimary,
                focusedBorderColor = mio.accent,
                unfocusedBorderColor = mio.line,
                cursorColor = mio.accent,
                errorBorderColor = mio.danger,
                errorCursorColor = mio.danger,
                errorSupportingTextColor = mio.danger,
            ),
        )
    }
}

/** Dropdown for fixed option lists (TTS voices). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MioDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "System default",
) {
    val mio = mioColors
    val dim = mioDimens
    var expanded by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().padding(vertical = dim.xs)) {
        Text(label.uppercase(), style = CaptionMono, color = mio.textSecondary)
        Spacer(Modifier.height(dim.xs))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = selected.ifBlank { placeholder },
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                textStyle = MioTypography.bodyLarge,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = mio.textPrimary,
                    unfocusedTextColor = if (selected.isBlank()) mio.textMuted else mio.textPrimary,
                    focusedBorderColor = mio.accent,
                    unfocusedBorderColor = mio.line,
                ),
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(),
            ) {
                DropdownMenuItem(
                    text = { Text(placeholder, style = MioTypography.bodyLarge) },
                    onClick = { onSelect(""); expanded = false },
                )
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, style = MioTypography.bodyLarge, maxLines = 1) },
                        onClick = { onSelect(option); expanded = false },
                    )
                }
            }
        }
    }
}
