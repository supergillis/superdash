package com.superdash.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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

@Composable
fun DoorbellEditDialog(
    initial: DoorbellConfig?,
    haEntities: List<EntityState> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (DoorbellConfig) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var trigger by remember { mutableStateOf(initial?.triggerEntity ?: "") }
    var camera by remember { mutableStateOf(initial?.cameraEntity ?: "") }
    var pickingTrigger by remember { mutableStateOf(false) }
    var pickingCamera by remember { mutableStateOf(false) }

    if (pickingTrigger) {
        HaEntityPickerDialog(
            title = "Trigger entity",
            entities = haEntities,
            selectedEntityId = trigger,
            allowedDomains = DoorbellTriggerDomains,
            manualLabel = "Trigger entity",
            onDismiss = { pickingTrigger = false },
            onSelectManual = { value ->
                trigger = value
                pickingTrigger = false
            },
            onSelect = { value ->
                trigger = value
                pickingTrigger = false
            },
        )
        return
    }

    if (pickingCamera) {
        HaEntityPickerDialog(
            title = "Camera entity",
            entities = haEntities,
            selectedEntityId = camera,
            allowedDomains = setOf("camera"),
            manualLabel = "Camera entity or stream URL",
            onDismiss = { pickingCamera = false },
            onSelectManual = { value ->
                camera = value
                pickingCamera = false
            },
            onSelect = { value ->
                camera = value
                pickingCamera = false
            },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (initial == null) {
                    "Add doorbell"
                } else {
                    "Edit doorbell"
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Front Door") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text("Trigger entity") },
                    supportingContent = {
                        Text(trigger.takeIf { value -> value.isNotBlank() } ?: "Not set")
                    },
                    modifier = Modifier.fillMaxWidth().clickable { pickingTrigger = true },
                )
                ListItem(
                    headlineContent = { Text("Camera entity or stream URL") },
                    supportingContent = {
                        Column {
                            Text(camera.takeIf { value -> value.isNotBlank() } ?: "Not set")
                            Text(
                                "Direct URLs (e.g. go2rtc) skip HA and start playback faster.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { pickingCamera = true },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && trigger.isNotBlank() && camera.isNotBlank(),
                onClick = {
                    val saved =
                        if (initial == null) {
                            DoorbellConfig.newWith(name.trim(), trigger.trim(), camera.trim())
                        } else {
                            initial.copy(
                                name = name.trim(),
                                triggerEntity = trigger.trim(),
                                cameraEntity = camera.trim(),
                            )
                        }
                    onSave(saved)
                },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private val DoorbellTriggerDomains =
    setOf(
        "binary_sensor",
        "event",
        "sensor",
        "button",
        "switch",
        "input_boolean",
        "input_button",
    )
