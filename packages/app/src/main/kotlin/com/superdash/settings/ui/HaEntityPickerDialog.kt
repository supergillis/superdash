package com.superdash.settings.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
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
import com.superdash.ha.EntityState
import com.superdash.settings.SettingsEditTextDialog
import kotlinx.serialization.json.JsonPrimitive

@Composable
fun HaEntityPickerDialog(
    title: String,
    entities: List<EntityState>,
    selectedEntityId: String,
    allowedDomains: Set<String>? = null,
    manualLabel: String = "Entity ID",
    onDismiss: () -> Unit,
    onSelectManual: (String) -> Unit,
    onSelect: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var editingManual by remember { mutableStateOf(false) }

    if (editingManual) {
        SettingsEditTextDialog(
            title = "Enter manually",
            initialValue = selectedEntityId,
            label = manualLabel,
            transformOnSave = { value -> value.trim() },
            onDismiss = { editingManual = false },
            onSave = { value ->
                onSelectManual(value)
            },
        )
        return
    }

    val filteredEntities =
        remember(entities, query, allowedDomains) {
            filterHaEntities(
                entities = entities,
                query = query,
                allowedDomains = allowedDomains,
            )
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { value -> query = value },
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                ) {
                    if (entities.isEmpty()) {
                        item {
                            ListItem(
                                headlineContent = { Text("No Home Assistant entities loaded") },
                                supportingContent = { Text("Use manual entry to keep typing an entity ID.") },
                            )
                        }
                    } else if (filteredEntities.isEmpty()) {
                        item {
                            ListItem(
                                headlineContent = { Text("No matching entities") },
                                supportingContent = { Text("Use manual entry to keep typing an entity ID.") },
                            )
                        }
                    } else {
                        items(filteredEntities, key = { entity -> entity.entityId }) { entity ->
                            val friendlyName = entity.friendlyName()
                            ListItem(
                                headlineContent = { Text(friendlyName ?: entity.entityId) },
                                supportingContent = {
                                    Column {
                                        if (friendlyName != null) {
                                            Text(entity.entityId)
                                        }
                                        Text(entity.state)
                                    }
                                },
                                trailingContent =
                                    if (entity.entityId == selectedEntityId) {
                                        {
                                            Icon(
                                                imageVector = Icons.Filled.Check,
                                                contentDescription = "Selected",
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable { onSelect(entity.entityId) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { editingManual = true }) {
                Text("Manual")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

fun filterHaEntities(
    entities: List<EntityState>,
    query: String,
    allowedDomains: Set<String>?,
): List<EntityState> {
    val normalizedQuery = query.trim().lowercase()
    val matchingEntities =
        entities
            .asSequence()
            .filter { entity ->
                val domain = entity.entityId.substringBefore('.')
                allowedDomains == null || domain in allowedDomains
            }.filter { entity ->
                if (normalizedQuery.isEmpty()) {
                    true
                } else {
                    val friendlyName = entity.friendlyName().orEmpty().lowercase()
                    entity.entityId.lowercase().contains(normalizedQuery) ||
                        friendlyName.contains(normalizedQuery)
                }
            }
    return matchingEntities.sortedBy { entity -> entity.entityId }.toList()
}

fun EntityState.friendlyName(): String? =
    (attributes["friendly_name"] as? JsonPrimitive)
        ?.content
        ?.takeIf { value -> value.isNotBlank() }
