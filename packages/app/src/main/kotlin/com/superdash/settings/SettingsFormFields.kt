package com.superdash.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.superdash.settings.ui.SettingsValueRow

data class SettingsChoice<T>(
    val value: T,
    val label: String,
    val supportingText: String? = null,
    val enabled: Boolean = true,
)

/**
 * Row that opens a [SettingsChoiceDialog] when clicked. The displayed value is
 * derived from [choices] (matching [selectedValue]); when no choice matches,
 * [fallback] is shown. Pass [dialogTitle] to override the dialog title.
 */
@Composable
fun <T> SettingsChoiceRow(
    label: String,
    choices: List<SettingsChoice<T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    dialogTitle: String = label,
    fallback: String = selectedValue.toString(),
) {
    var editing by remember { mutableStateOf(false) }
    val displayValue = choices.firstOrNull { choice -> choice.value == selectedValue }?.label ?: fallback

    SettingsValueRow(
        label = label,
        value = displayValue,
        modifier = modifier,
        onClick = { editing = true },
    )

    if (editing) {
        SettingsChoiceDialog(
            title = dialogTitle,
            choices = choices,
            selectedValue = selectedValue,
            onDismiss = { editing = false },
            onSelect = { value ->
                onSelect(value)
                editing = false
            },
        )
    }
}

@Composable
fun SettingsEditTextDialog(
    title: String,
    initialValue: String,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    transformOnSave: (String) -> String = { it },
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { value -> draft = value },
                label = { Text(label) },
                keyboardOptions = keyboardOptions,
                visualTransformation = visualTransformation,
                singleLine = singleLine,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(transformOnSave(draft))
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
fun <T> SettingsChoiceDialog(
    title: String,
    choices: List<SettingsChoice<T>>,
    selectedValue: T,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
            ) {
                items(choices) { choice ->
                    val isSelected = choice.value == selectedValue
                    val rowAlpha =
                        if (choice.enabled) {
                            1f
                        } else {
                            0.38f
                        }
                    val rowModifier =
                        Modifier
                            .fillMaxWidth()
                            .alpha(rowAlpha)
                            .clickable(enabled = choice.enabled) {
                                onSelect(choice.value)
                            }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = rowModifier.padding(vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(choice.label)
                            if (choice.supportingText != null) {
                                Text(choice.supportingText)
                            }
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
