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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.superdash.R
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
        VoiceResponseMode.entries.map { mode -> SettingsChoice(mode.key, stringResource(mode.labelRes)) }
    val wakeWordChoices =
        WakeWordModel.supported.map { model -> SettingsChoice(model.id, model.label) }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_voice_enabled_title)) },
        supportingContent = { Text(stringResource(R.string.settings_voice_enabled_summary)) },
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
            label = stringResource(R.string.settings_voice_wake_word_label),
            choices = wakeWordChoices,
            selectedValue = state.activeWakeWord,
            fallback = state.activeWakeWord,
            onSelect = actions.onActiveWakeWordChange,
        )
        SettingsChoiceRow(
            label = stringResource(R.string.settings_voice_response_mode_label),
            choices = responseModeChoices,
            selectedValue = state.responseMode.key,
            fallback = stringResource(state.responseMode.labelRes),
            onSelect = { value -> actions.onVoiceResponseModeChange(VoiceResponseMode.fromKey(value)) },
        )
        SettingsActionRow(
            label = stringResource(R.string.settings_voice_speech_pipeline_title),
            supportingText =
                stringResource(
                    R.string.settings_voice_speech_pipeline_summary,
                    stringResource(state.primarySttProvider.labelRes),
                    stringResource(state.secondarySttProvider.labelRes),
                ),
            onClick = onOpenSpeechPipeline,
        )
        SettingsActionRow(
            label = stringResource(R.string.settings_voice_local_models_title),
            supportingText =
                stringResource(
                    R.string.settings_voice_local_models_summary,
                    state.selectedSttModelId,
                    state.selectedIntentEmbeddingModelId,
                ),
            onClick = onOpenLocalModels,
        )
        SettingsActionRow(
            label = stringResource(R.string.settings_voice_command_recording_title),
            supportingText =
                if (state.commandRecordingEnabled) {
                    pluralStringResource(
                        R.plurals.settings_voice_command_recording_summary_on,
                        state.commandRecordingRetention,
                        state.commandRecordingRetention,
                    )
                } else {
                    stringResource(R.string.settings_voice_command_recording_summary_off)
                },
            onClick = onOpenCommandRecording,
        )
        SettingsActionRow(
            label = stringResource(R.string.settings_voice_advanced_tuning_title),
            supportingText = stringResource(R.string.settings_voice_silence_timeout, state.vadSilenceMs),
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
        ).map { provider -> SettingsChoice(provider.key, stringResource(provider.labelRes)) }
    val primarySttChoices =
        listOf(
            VoiceSttProvider.HaAssist,
            VoiceSttProvider.Whisper,
            VoiceSttProvider.Moonshine,
        ).map { provider -> SettingsChoice(provider.key, stringResource(provider.labelRes)) }
    val secondarySttChoices =
        VoiceSttProvider.entries.map { provider -> SettingsChoice(provider.key, stringResource(provider.labelRes)) }

    if (state.voiceEnabled) {
        SettingsChoiceRow(
            label = stringResource(R.string.settings_voice_assist_provider_label),
            choices = assistChoices,
            selectedValue = state.assistProvider.key,
            fallback = stringResource(state.assistProvider.labelRes),
            onSelect = { value -> actions.onVoiceAssistProviderChange(VoiceSttProvider.fromKey(value)) },
        )
        SettingsChoiceRow(
            label = stringResource(R.string.settings_voice_primary_stt_label),
            choices = primarySttChoices,
            selectedValue = state.primarySttProvider.key,
            fallback = stringResource(state.primarySttProvider.labelRes),
            onSelect = { value -> actions.onPrimarySttProviderChange(VoiceSttProvider.fromKey(value)) },
        )
        SettingsChoiceRow(
            label = stringResource(R.string.settings_voice_secondary_stt_label),
            choices = secondarySttChoices,
            selectedValue = state.secondarySttProvider.key,
            fallback = stringResource(state.secondarySttProvider.labelRes),
            onSelect = { value -> actions.onSecondarySttProviderChange(VoiceSttProvider.fromKey(value)) },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_voice_local_intent_recognizer_title)) },
            supportingContent = { Text(stringResource(R.string.settings_voice_local_intent_recognizer_summary)) },
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
            label = stringResource(R.string.settings_voice_stt_model_label),
            choices = sttModelChoices,
            selectedValue = state.selectedSttModelId,
            fallback = state.selectedSttModelId,
            onSelect = actions.onSelectedSttModelChange,
        )
        SettingsChoiceRow(
            label = stringResource(R.string.settings_voice_intent_embedding_model_label),
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
            headlineContent = { Text(stringResource(R.string.settings_voice_save_command_recordings_title)) },
            supportingContent = {
                Text(stringResource(R.string.settings_voice_save_command_recordings_summary))
            },
            trailingContent = {
                Switch(
                    checked = state.commandRecordingEnabled,
                    onCheckedChange = actions.onCommandRecordingEnabledChange,
                )
            },
        )
        if (state.commandRecordingEnabled) {
            Text(
                stringResource(R.string.settings_voice_keep_recordings, state.commandRecordingRetention),
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
                label = stringResource(R.string.settings_voice_clear_command_recordings_label),
                onClick = { confirmingClearRecordings = true },
            )
            if (confirmingClearRecordings) {
                AlertDialog(
                    onDismissRequest = { confirmingClearRecordings = false },
                    title = { Text(stringResource(R.string.settings_voice_clear_command_recordings_confirm_title)) },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                actions.onClearCommandRecordings()
                                confirmingClearRecordings = false
                            },
                        ) {
                            Text(stringResource(R.string.settings_action_clear))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmingClearRecordings = false }) {
                            Text(stringResource(R.string.settings_action_cancel))
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
            stringResource(R.string.settings_voice_silence_timeout, state.vadSilenceMs),
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
                                stringResource(R.string.settings_voice_model_selected)
                            } else {
                                stringResource(R.string.settings_voice_model_available)
                            }
                        VoiceModelInstallStatus.NotInstalled ->
                            stringResource(
                                R.string.settings_voice_model_not_installed,
                            )
                        VoiceModelInstallStatus.Downloading -> stringResource(R.string.settings_voice_model_downloading)
                        VoiceModelInstallStatus.Failed ->
                            model.error ?: stringResource(R.string.settings_voice_model_install_failed)
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
                            Icon(
                                Icons.Filled.Download,
                                contentDescription =
                                    stringResource(
                                        R.string.settings_voice_model_download_content_description,
                                        model.label,
                                    ),
                            )
                        }
                    }
                    model.canDelete -> {
                        IconButton(onClick = { actions.onDeleteVoiceModel(model.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription =
                                    stringResource(
                                        R.string.settings_voice_model_delete_content_description,
                                        model.label,
                                    ),
                            )
                        }
                    }
                }
            },
        )
    }
}
