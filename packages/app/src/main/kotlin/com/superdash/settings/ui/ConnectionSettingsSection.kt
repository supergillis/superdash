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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.superdash.R
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
    val notSet = stringResource(R.string.settings_value_not_set)

    SettingsTextEditRow(
        label = stringResource(R.string.settings_connection_ha_url_label),
        value = state.haUrl?.takeIf { it.isNotBlank() } ?: notSet,
        dialogTitle = stringResource(R.string.settings_connection_ha_url_dialog_title),
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
    ) { Text(stringResource(R.string.settings_connection_test_button)) }
    Button(
        onClick = actions.onReauthenticate,
        modifier = Modifier.padding(horizontal = 16.dp),
    ) { Text(stringResource(R.string.settings_connection_reauthenticate_button)) }
    testResult?.let { ok ->
        Text(
            if (ok) {
                stringResource(R.string.settings_connection_reachable)
            } else {
                stringResource(R.string.settings_connection_unreachable)
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }

    SettingsTextEditRow(
        label = stringResource(R.string.settings_connection_pinned_dashboard_label),
        value = state.dashboardPath.takeIf { it.isNotBlank() } ?: notSet,
        dialogTitle = stringResource(R.string.settings_connection_pinned_dashboard_dialog_title),
        initialValue = state.dashboardPath,
        supportingText = stringResource(R.string.settings_connection_pinned_dashboard_summary),
        transformOnSave = { value -> value.trim() },
        onSave = actions.onDashboardPathChange,
    )

    ConnectionStatusRow(state = state.haState, entityCount = state.entityCount)
}
