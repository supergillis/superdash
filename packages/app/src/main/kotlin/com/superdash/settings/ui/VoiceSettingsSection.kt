package com.superdash.settings.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
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
import com.superdash.settings.SettingsChoice
import com.superdash.settings.SettingsChoiceRow
import com.superdash.settings.VoiceSettingsActions
import com.superdash.settings.VoiceSettingsState
import com.superdash.voice.models.VoiceModelInstallStatus
import com.superdash.voice.models.VoiceModelKind
import com.superdash.voice.pipeline.VoiceResponseMode
import com.superdash.voice.pipeline.VoiceSttProvider
import com.superdash.voice.wake.WakeWordModel

@Composable
fun VoiceSettingsSection(
    state: VoiceSettingsState,
    actions: VoiceSettingsActions,
    onOpenSpeechPipeline: () -> Unit = {},
    onOpenLocalModels: () -> Unit = {},
    onOpenCommandRecording: () -> Unit = {},
    onOpenAdvancedTuning: () -> Unit = {},
) {
    val responseModeChoices =
        VoiceResponseMode.entries.map { mode -> SettingsChoice(mode.key, mode.label) }
    val wakeWordChoices =
        WakeWordModel.supported.map { model -> SettingsChoice(model.id, model.label) }

    ListItem(
        headlineContent = { Text("Voice enabled") },
        supportingContent = { Text("On-device wake word and HA Assist") },
        trailingContent = {
            Switch(
                checked = state.voiceEnabled,
                onCheckedChange = { wanted ->
                    if (wanted) {
                        actions.onRequestVoiceEnable()
                    } else {
                        actions.onVoiceDisable()
                    }
                },
            )
        },
    )
    if (state.voiceEnabled) {
        SettingsChoiceRow(
            label = "Wake word",
            choices = wakeWordChoices,
            selectedValue = state.activeWakeWord,
            fallback = state.activeWakeWord,
            onSelect = actions.onActiveWakeWordChange,
        )
        SettingsChoiceRow(
            label = "Response mode",
            choices = responseModeChoices,
            selectedValue = state.responseMode.key,
            fallback = state.responseMode.label,
            onSelect = { value -> actions.onVoiceResponseModeChange(VoiceResponseMode.fromKey(value)) },
        )
        SettingsActionRow(
            label = "Speech pipeline",
            supportingText = "${state.primarySttProvider.label} primary, ${state.secondarySttProvider.label} secondary",
            onClick = onOpenSpeechPipeline,
        )
        SettingsActionRow(
            label = "Local models",
            supportingText = "${state.selectedSttModelId}, ${state.selectedIntentEmbeddingModelId}",
            onClick = onOpenLocalModels,
        )
        SettingsActionRow(
            label = "Command recording",
            supportingText =
                if (state.commandRecordingEnabled) {
                    "Keeping ${state.commandRecordingRetention} recordings"
                } else {
                    "Off"
                },
            onClick = onOpenCommandRecording,
        )
        SettingsActionRow(
            label = "Advanced tuning",
            supportingText = "Silence timeout: ${state.vadSilenceMs} ms",
            onClick = onOpenAdvancedTuning,
        )
    }
}

@Composable
fun VoiceSpeechPipelineSettingsSection(
    state: VoiceSettingsState,
    actions: VoiceSettingsActions,
) {
    val assistChoices =
        listOf(
            VoiceSttProvider.HaAssist,
            VoiceSttProvider.Whisper,
            VoiceSttProvider.Moonshine,
        ).map { provider -> SettingsChoice(provider.key, provider.label) }
    val primarySttChoices =
        listOf(
            VoiceSttProvider.HaAssist,
            VoiceSttProvider.Whisper,
            VoiceSttProvider.Moonshine,
        ).map { provider -> SettingsChoice(provider.key, provider.label) }
    val secondarySttChoices =
        VoiceSttProvider.entries.map { provider -> SettingsChoice(provider.key, provider.label) }

    if (state.voiceEnabled) {
        SettingsChoiceRow(
            label = "Assist provider",
            choices = assistChoices,
            selectedValue = state.assistProvider.key,
            fallback = state.assistProvider.label,
            onSelect = { value -> actions.onVoiceAssistProviderChange(VoiceSttProvider.fromKey(value)) },
        )
        SettingsChoiceRow(
            label = "Primary STT",
            choices = primarySttChoices,
            selectedValue = state.primarySttProvider.key,
            fallback = state.primarySttProvider.label,
            onSelect = { value -> actions.onPrimarySttProviderChange(VoiceSttProvider.fromKey(value)) },
        )
        SettingsChoiceRow(
            label = "Secondary STT",
            choices = secondarySttChoices,
            selectedValue = state.secondarySttProvider.key,
            fallback = state.secondarySttProvider.label,
            onSelect = { value -> actions.onSecondarySttProviderChange(VoiceSttProvider.fromKey(value)) },
        )
        ListItem(
            headlineContent = { Text("Local intent recognizer") },
            supportingContent = { Text("Try cached entity commands before HA text") },
            trailingContent = {
                Switch(
                    checked = state.localIntentRecognizerEnabled,
                    onCheckedChange = actions.onLocalIntentRecognizerEnabledChange,
                )
            },
        )
    }
}

@Composable
fun VoiceLocalModelsSettingsSection(
    state: VoiceSettingsState,
    actions: VoiceSettingsActions,
) {
    val sttModelChoices =
        state.voiceModels
            .filter { model -> model.kind == VoiceModelKind.Stt && model.status == VoiceModelInstallStatus.Available }
            .map { model -> SettingsChoice(model.id, model.label) }
    val intentEmbeddingChoices =
        state.voiceModels
            .filter { model ->
                model.kind == VoiceModelKind.IntentEmbedding && model.status == VoiceModelInstallStatus.Available
            }.map { model -> SettingsChoice(model.id, model.label) }

    if (state.voiceEnabled) {
        SettingsChoiceRow(
            label = "STT model",
            choices = sttModelChoices,
            selectedValue = state.selectedSttModelId,
            fallback = state.selectedSttModelId,
            onSelect = actions.onSelectedSttModelChange,
        )
        SettingsChoiceRow(
            label = "Intent embedding model",
            choices = intentEmbeddingChoices,
            selectedValue = state.selectedIntentEmbeddingModelId,
            fallback = state.selectedIntentEmbeddingModelId,
            onSelect = actions.onSelectedIntentEmbeddingModelChange,
        )
        VoiceModelStatusRows(state = state, actions = actions)
    }
}

@Composable
fun VoiceCommandRecordingSettingsSection(
    state: VoiceSettingsState,
    actions: VoiceSettingsActions,
) {
    var confirmingClearRecordings by remember { mutableStateOf(false) }

    if (state.voiceEnabled) {
        ListItem(
            headlineContent = { Text("Save command recordings") },
            supportingContent = { Text("Store real command audio for review and local STT backtesting") },
            trailingContent = {
                Switch(
                    checked = state.commandRecordingEnabled,
                    onCheckedChange = actions.onCommandRecordingEnabledChange,
                )
            },
        )
        if (state.commandRecordingEnabled) {
            Text(
                "Keep recordings: ${state.commandRecordingRetention}",
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Slider(
                value = state.commandRecordingRetention.toFloat(),
                onValueChange = { value ->
                    actions.onCommandRecordingRetentionChange((value / 25).toInt() * 25)
                },
                valueRange = 0f..500f,
                steps = 19,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            SettingsActionRow(
                label = "Clear command recordings",
                onClick = { confirmingClearRecordings = true },
            )
            if (confirmingClearRecordings) {
                AlertDialog(
                    onDismissRequest = { confirmingClearRecordings = false },
                    title = { Text("Clear command recordings?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                actions.onClearCommandRecordings()
                                confirmingClearRecordings = false
                            },
                        ) {
                            Text("Clear")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmingClearRecordings = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
    }
}

@Composable
fun VoiceAdvancedTuningSettingsSection(
    state: VoiceSettingsState,
    actions: VoiceSettingsActions,
) {
    if (state.voiceEnabled) {
        Text(
            "Silence timeout: ${state.vadSilenceMs} ms",
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Slider(
            value = state.vadSilenceMs.toFloat(),
            onValueChange = { value ->
                actions.onVadSilenceMsChange((value / 250).toInt() * 250)
            },
            valueRange = 250f..2500f,
            steps = 8,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun VoiceModelStatusRows(
    state: VoiceSettingsState,
    actions: VoiceSettingsActions,
) {
    for (model in state.voiceModels) {
        ListItem(
            headlineContent = { Text(model.label) },
            supportingContent = {
                val languageLabels = model.languages.joinToString { language -> language.label }
                val status =
                    when (model.status) {
                        VoiceModelInstallStatus.Available ->
                            if (model.selected) {
                                "Selected"
                            } else {
                                "Available"
                            }
                        VoiceModelInstallStatus.NotInstalled -> "Not installed"
                        VoiceModelInstallStatus.Downloading -> "Downloading"
                        VoiceModelInstallStatus.Failed -> model.error ?: "Install failed"
                    }
                Text(
                    listOf(
                        model.summary,
                        languageLabels,
                        status,
                    ).filter { value ->
                        value.isNotBlank()
                    }.joinToString("\n"),
                )
            },
            trailingContent = {
                when {
                    model.status == VoiceModelInstallStatus.Downloading -> {
                        LinearProgressIndicator(
                            progress = {
                                if (model.totalBytes == 0L) {
                                    0f
                                } else {
                                    model.downloadedBytes.toFloat() / model.totalBytes.toFloat()
                                }
                            },
                            modifier = Modifier.width(96.dp),
                        )
                    }
                    model.canDownload -> {
                        IconButton(onClick = { actions.onDownloadVoiceModel(model.id) }) {
                            Icon(Icons.Filled.Download, contentDescription = "Download ${model.label}")
                        }
                    }
                    model.canDelete -> {
                        IconButton(onClick = { actions.onDeleteVoiceModel(model.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete ${model.label}")
                        }
                    }
                }
            },
        )
    }
}
