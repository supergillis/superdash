package com.superdash.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.VisualTransformation
import com.superdash.settings.SettingsEditTextDialog

@Composable
fun SettingsValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = {
            Column {
                Text(value)
                if (supportingText != null) {
                    Text(
                        supportingText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        modifier =
            if (onClick != null) {
                modifier.clickable(enabled = enabled, onClick = onClick)
            } else {
                modifier
            },
    )
}

@Composable
fun SettingsTextEditRow(
    label: String,
    value: String,
    dialogTitle: String,
    initialValue: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    transformOnSave: (String) -> String = { it },
) {
    var editing by remember { mutableStateOf(false) }

    SettingsValueRow(
        label = label,
        value = value,
        modifier = modifier,
        supportingText = supportingText,
        onClick = { editing = true },
    )

    if (editing) {
        SettingsEditTextDialog(
            title = dialogTitle,
            initialValue = initialValue,
            label = label,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            visualTransformation = visualTransformation,
            transformOnSave = transformOnSave,
            onDismiss = { editing = false },
            onSave = { newValue ->
                onSave(newValue)
                editing = false
            },
        )
    }
}

@Composable
fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent =
            if (supportingText != null) {
                { Text(supportingText) }
            } else {
                null
            },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
            )
        },
        modifier = modifier,
    )
}

@Composable
fun SettingsActionRow(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent =
            if (supportingText != null) {
                { Text(supportingText) }
            } else {
                null
            },
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
fun SettingsNavigationRow(
    label: String,
    supportingText: String,
    icon: ImageVector,
    selected: Boolean,
    exposeSelection: Boolean,
    showChevron: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String? = null,
) {
    val tagModifier =
        if (testTag != null) {
            Modifier.testTag(testTag)
        } else {
            Modifier
        }

    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(supportingText) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
            )
        },
        trailingContent =
            if (showChevron) {
                {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowRight,
                        contentDescription = null,
                    )
                }
            } else {
                null
            },
        colors =
            ListItemDefaults.colors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.surfaceVariant
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        modifier =
            modifier
                .then(tagModifier)
                .then(
                    if (exposeSelection) {
                        Modifier
                            .semantics { this.selected = selected }
                            .selectable(
                                selected = selected,
                                role = Role.Tab,
                                onClick = onClick,
                            )
                    } else {
                        Modifier.clickable(onClick = onClick)
                    },
                ),
    )
}

@Composable
fun SettingsSectionTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun SettingsDetailHeader(
    title: String,
    summary: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
        )
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
