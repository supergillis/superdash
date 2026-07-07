package com.superdash.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.superdash.R
import com.superdash.core.util.UrlNormalizer
import com.superdash.settings.ImmichSettingsActions
import com.superdash.settings.ImmichSettingsState
import kotlinx.coroutines.launch

@Composable
fun ImmichSettingsSection(
    state: ImmichSettingsState,
    actions: ImmichSettingsActions,
) {
    val notSet = stringResource(R.string.settings_value_not_set)
    SettingsTextEditRow(
        label = stringResource(R.string.settings_immich_url_label),
        value = state.url.takeIf { it.isNotBlank() } ?: notSet,
        dialogTitle = stringResource(R.string.settings_immich_url_dialog_title),
        initialValue = state.url,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        transformOnSave = { value -> UrlNormalizer.normalize(value) ?: value },
        onSave = actions.onImmichUrlChange,
    )
    SettingsTextEditRow(
        label = stringResource(R.string.settings_immich_api_key_label),
        value =
            if (state.apiKey.isBlank()) {
                notSet
            } else {
                stringResource(R.string.settings_immich_api_key_set)
            },
        dialogTitle = stringResource(R.string.settings_immich_api_key_dialog_title),
        initialValue = state.apiKey,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        visualTransformation = PasswordVisualTransformation(),
        transformOnSave = { value -> value.trim() },
        onSave = actions.onImmichApiKeyChange,
    )
    SettingsTextEditRow(
        label = stringResource(R.string.settings_immich_album_label),
        value = state.album.takeIf { it.isNotBlank() } ?: stringResource(R.string.settings_immich_album_whole_library),
        dialogTitle = stringResource(R.string.settings_immich_album_dialog_title),
        initialValue = state.album,
        transformOnSave = { value -> value.trim() },
        onSave = actions.onImmichAlbumChange,
    )
    SettingsTextEditRow(
        label = stringResource(R.string.settings_immich_refresh_hours_label),
        value = state.catalogTtlHours.toString(),
        dialogTitle = stringResource(R.string.settings_immich_refresh_hours_dialog_title),
        initialValue = state.catalogTtlHours.toString(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        transformOnSave = { value -> value.trim() },
        onSave = { value ->
            value.toIntOrNull()?.coerceIn(1, 168)?.let { actions.onImmichCatalogTtlHoursChange(it) }
        },
    )
    ImmichRefreshRow(onRefresh = actions.onRefreshImmichCatalog)
    ImmichTestRow(
        url = state.url,
        apiKey = state.apiKey,
        album = state.album,
        onTestImmich = actions.onTestImmich,
    )
}

@Composable
private fun ImmichRefreshRow(onRefresh: suspend () -> String) {
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val refreshingStatus = stringResource(R.string.settings_immich_refreshing_status)
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                running = true
                status = refreshingStatus
                scope.launch {
                    status = onRefresh()
                    running = false
                }
            },
            enabled = !running,
        ) { Text(stringResource(R.string.settings_immich_refresh_button)) }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun ImmichTestRow(
    url: String,
    apiKey: String,
    album: String,
    onTestImmich: suspend (url: String, apiKey: String, album: String) -> String,
) {
    var status by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val testingStatus = stringResource(R.string.settings_immich_testing_status)
    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = {
                running = true
                status = testingStatus
                scope.launch {
                    status = onTestImmich(url, apiKey, album)
                    running = false
                }
            },
            enabled = !running && url.isNotBlank() && apiKey.isNotBlank(),
        ) { Text(stringResource(R.string.settings_immich_test_button)) }
        status?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}
