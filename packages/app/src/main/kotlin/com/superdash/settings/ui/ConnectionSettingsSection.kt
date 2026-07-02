package com.superdash.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.superdash.core.util.UrlNormalizer
import com.superdash.kiosk.ui.ConnectionStatusRow
import com.superdash.settings.ConnectionSettingsActions
import com.superdash.settings.ConnectionSettingsState
import kotlinx.coroutines.launch

@Composable
fun ConnectionSettingsSection(
    state: ConnectionSettingsState,
    actions: ConnectionSettingsActions,
) {
    var testResult by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()

    SettingsTextEditRow(
        label = "HA URL",
        value = state.haUrl?.takeIf { it.isNotBlank() } ?: "Not set",
        dialogTitle = "Edit HA URL",
        initialValue = state.haUrl.orEmpty(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        transformOnSave = { value -> UrlNormalizer.normalize(value) ?: "" },
        onSave = actions.onHaUrlChange,
    )
    Button(
        onClick = {
            scope.launch { testResult = actions.onTestConnection(state.haUrl.orEmpty()) }
        },
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Test connection") }
    Button(
        onClick = actions.onReauthenticate,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text("Reauthenticate HA") }
    testResult?.let { ok ->
        Text(
            if (ok) "✓ HA reachable" else "✗ Could not reach HA at this URL",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    SettingsTextEditRow(
        label = "Pinned dashboard",
        value = state.dashboardPath.takeIf { it.isNotBlank() } ?: "Not set",
        dialogTitle = "Edit pinned dashboard",
        initialValue = state.dashboardPath,
        supportingText = "Locks to this dashboard. Leave empty for no lock.",
        transformOnSave = { value -> value.trim() },
        onSave = actions.onDashboardPathChange,
    )

    ConnectionStatusRow(state = state.haState, entityCount = state.entityCount)
}
