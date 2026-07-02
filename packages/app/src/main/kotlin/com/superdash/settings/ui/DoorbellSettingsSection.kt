package com.superdash.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.superdash.doorbell.DoorbellConfig
import com.superdash.ha.EntityState
import com.superdash.settings.DoorbellSettingsActions
import com.superdash.settings.DoorbellSettingsState
import com.superdash.settings.EsphomeSettingsActions
import com.superdash.settings.EsphomeSettingsState
import com.superdash.settings.PskState

@Composable
fun DoorbellSettingsSection(
    state: DoorbellSettingsState,
    haEntities: List<EntityState>,
    actions: DoorbellSettingsActions,
) {
    var editingDoorbell: DoorbellConfig? by remember { mutableStateOf(null) }
    var addingDoorbell by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text("Doorbell overlay") },
        supportingContent = { Text("Show camera feed when a doorbell rings") },
        trailingContent = {
            Switch(
                checked = state.enabled,
                onCheckedChange = actions.onDoorbellEnabledChange,
            )
        },
    )
    if (state.enabled) {
        Text(
            if (state.autoCloseSec == 0) {
                "Auto-close: never"
            } else {
                "Auto-close: ${state.autoCloseSec}s"
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Slider(
            value = state.autoCloseSec.toFloat(),
            onValueChange = { value ->
                val snapped = (value / 10).toInt() * 10
                actions.onDoorbellAutoCloseSecChange(snapped)
            },
            valueRange = 0f..300f,
            steps = 29,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Text(
            "Doorbells",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        if (state.configs.isEmpty()) {
            Text(
                "Add at least one doorbell.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            for (config in state.configs) {
                ListItem(
                    headlineContent = { Text(config.name) },
                    supportingContent = {
                        Column {
                            Text(config.triggerEntity, style = MaterialTheme.typography.bodySmall)
                            Text(config.cameraEntity, style = MaterialTheme.typography.bodySmall)
                        }
                    },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { actions.onTestDoorbell(config) }) { Text("Test") }
                            TextButton(onClick = { editingDoorbell = config }) { Text("Edit") }
                            TextButton(
                                onClick = {
                                    actions.onRemoveDoorbell(config.id)
                                },
                            ) { Text("Delete") }
                        }
                    },
                )
            }
        }
        Button(
            onClick = { addingDoorbell = true },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text("+ Add doorbell") }
    }

    if (addingDoorbell) {
        DoorbellEditDialog(
            initial = null,
            haEntities = haEntities,
            onDismiss = { addingDoorbell = false },
            onSave = { saved ->
                actions.onUpsertDoorbell(saved)
                addingDoorbell = false
            },
        )
    }
    editingDoorbell?.let { config ->
        DoorbellEditDialog(
            initial = config,
            haEntities = haEntities,
            onDismiss = { editingDoorbell = null },
            onSave = { saved ->
                actions.onUpsertDoorbell(saved)
                editingDoorbell = null
            },
        )
    }
}

@Composable
fun EsphomeSettingsSection(
    state: EsphomeSettingsState,
    actions: EsphomeSettingsActions,
) {
    ListItem(
        headlineContent = { Text("Enabled") },
        supportingContent = {
            Text("Expose superdash to HA via the ESPHome protocol.")
        },
        trailingContent = {
            Switch(checked = state.enabled, onCheckedChange = actions.onEsphomeEnabledChange)
        },
    )

    var draft by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val configuredHint =
        when (val pskState = state.pskState) {
            is PskState.NotSet -> "Not set — plaintext is in use."
            is PskState.Configured ->
                "Configured (${pskState.fingerprint}). Replace by entering a new value."
        }

    ListItem(
        headlineContent = { Text("Encryption key (base64)") },
        supportingContent = {
            Column {
                Text(configuredHint)
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        error = null
                    },
                    placeholder = { Text("32-byte key, base64-encoded") },
                    isError = error != null,
                    supportingText = {
                        error?.let { Text(it) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row {
                    TextButton(
                        onClick = {
                            if (actions.onSavePskBase64(draft)) {
                                draft = ""
                                error = null
                            } else {
                                error = "Key must decode to exactly 32 bytes."
                            }
                        },
                    ) { Text("Save") }
                    TextButton(
                        onClick = {
                            actions.onClearPsk()
                            draft = ""
                            error = null
                        },
                    ) { Text("Clear") }
                }
            }
        },
    )
}
