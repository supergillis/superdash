package com.superdash.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.superdash.R
import com.superdash.feed.FeedConfig
import com.superdash.feed.FeedTrigger
import com.superdash.ha.EntityState

@Composable
fun FeedEditDialog(
    initial: FeedConfig?,
    haEntities: List<EntityState> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (FeedConfig) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var trigger by remember { mutableStateOf(initial?.triggerEntity ?: "") }
    var camera by remember { mutableStateOf(initial?.cameraEntity ?: "") }
    var pickingTrigger by remember { mutableStateOf(false) }
    var pickingCamera by remember { mutableStateOf(false) }

    var sustained by remember { mutableStateOf(initial?.trigger is FeedTrigger.Sustained) }
    var activeStates by
        remember {
            mutableStateOf(
                (initial?.trigger as? FeedTrigger.Sustained)?.activeStates?.joinToString(", ") ?: "on",
            )
        }
    var autoCloseSec by remember { mutableStateOf((initial?.autoCloseSec ?: 60).toString()) }
    var wakeScreen by remember { mutableStateOf(initial?.wakeScreen ?: true) }
    var order by remember { mutableStateOf((initial?.order ?: 0).toString()) }
    var orderEdited by remember { mutableStateOf(false) }

    LaunchedEffect(sustained) {
        if (initial == null && !orderEdited) {
            order =
                if (sustained) {
                    "0"
                } else {
                    "10"
                }
        }
    }

    if (pickingTrigger) {
        HaEntityPickerDialog(
            title = stringResource(R.string.settings_feed_trigger_entity),
            entities = haEntities,
            selectedEntityId = trigger,
            allowedDomains =
                if (sustained) {
                    SustainedTriggerDomains
                } else {
                    FeedTriggerDomains
                },
            manualLabel = stringResource(R.string.settings_feed_trigger_entity),
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
            title = stringResource(R.string.settings_feed_camera_entity),
            entities = haEntities,
            selectedEntityId = camera,
            allowedDomains = setOf("camera"),
            manualLabel = stringResource(R.string.settings_feed_camera_entity_or_url),
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
                    stringResource(R.string.settings_feed_dialog_add_title)
                } else {
                    stringResource(R.string.settings_feed_dialog_edit_title)
                },
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_feed_name_label)) },
                    placeholder = { Text(stringResource(R.string.settings_feed_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_feed_trigger_entity)) },
                    supportingContent = {
                        Text(
                            trigger.takeIf { value -> value.isNotBlank() }
                                ?: stringResource(R.string.settings_value_not_set),
                        )
                    },
                    modifier = Modifier.fillMaxWidth().clickable { pickingTrigger = true },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_feed_camera_entity_or_url)) },
                    supportingContent = {
                        Column {
                            Text(
                                camera.takeIf { value -> value.isNotBlank() }
                                    ?: stringResource(R.string.settings_value_not_set),
                            )
                            Text(
                                stringResource(R.string.settings_feed_camera_url_hint),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().clickable { pickingCamera = true },
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_feed_mode_label)) },
                    supportingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !sustained,
                                onClick = { sustained = false },
                                label = { Text(stringResource(R.string.settings_feed_mode_momentary)) },
                            )
                            FilterChip(
                                selected = sustained,
                                onClick = { sustained = true },
                                label = { Text(stringResource(R.string.settings_feed_mode_sustained)) },
                            )
                        }
                    },
                )
                if (sustained) {
                    OutlinedTextField(
                        value = activeStates,
                        onValueChange = { activeStates = it },
                        label = { Text(stringResource(R.string.settings_feed_active_states_label)) },
                        supportingText = { Text(stringResource(R.string.settings_feed_active_states_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = autoCloseSec,
                    onValueChange = { value -> autoCloseSec = value.filter { it.isDigit() }.take(3) },
                    label = { Text(stringResource(R.string.settings_feed_auto_close_label)) },
                    supportingText = { Text(stringResource(R.string.settings_feed_auto_close_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = order,
                    onValueChange = { value ->
                        orderEdited = true
                        order = value.filter { it.isDigit() }.take(3)
                    },
                    label = { Text(stringResource(R.string.settings_feed_order_label)) },
                    supportingText = { Text(stringResource(R.string.settings_feed_order_hint)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_feed_wake_screen_label)) },
                    supportingContent = { Text(stringResource(R.string.settings_feed_wake_screen_hint)) },
                    trailingContent = {
                        Switch(checked = wakeScreen, onCheckedChange = { wakeScreen = it })
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && trigger.isNotBlank() && camera.isNotBlank(),
                onClick = {
                    val parsedTrigger =
                        if (sustained) {
                            FeedTrigger.Sustained(
                                activeStates =
                                    activeStates
                                        .split(',')
                                        .map { it.trim() }
                                        .filter { it.isNotEmpty() }
                                        .ifEmpty { listOf("on") },
                            )
                        } else {
                            FeedTrigger.Momentary
                        }
                    val parsedAutoCloseSec = autoCloseSec.toIntOrNull() ?: 0
                    val parsedOrder = order.toIntOrNull() ?: 0
                    val saved =
                        if (initial == null) {
                            val base = FeedConfig.newWith(name.trim(), trigger.trim(), camera.trim())
                            base.copy(
                                trigger = parsedTrigger,
                                autoCloseSec = parsedAutoCloseSec,
                                wakeScreen = wakeScreen,
                                order = parsedOrder,
                            )
                        } else {
                            initial.copy(
                                name = name.trim(),
                                triggerEntity = trigger.trim(),
                                cameraEntity = camera.trim(),
                                trigger = parsedTrigger,
                                autoCloseSec = parsedAutoCloseSec,
                                wakeScreen = wakeScreen,
                                order = parsedOrder,
                            )
                        }
                    onSave(saved)
                },
            ) { Text(stringResource(R.string.settings_action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_action_cancel)) }
        },
    )
}

private val FeedTriggerDomains =
    setOf(
        "binary_sensor",
        "event",
        "sensor",
        "button",
        "switch",
        "input_boolean",
        "input_button",
    )

private val SustainedTriggerDomains =
    setOf(
        "binary_sensor",
        "camera",
        "sensor",
        "switch",
        "input_boolean",
        "media_player",
    )
