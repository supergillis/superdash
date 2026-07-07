package com.superdash.settings

import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.observe
import com.superdash.core.persistence.write
import com.superdash.voice.VoiceSettings
import com.superdash.voice.models.VoiceModelIds
import com.superdash.voice.pipeline.VoiceResponseMode
import com.superdash.voice.pipeline.VoiceSttProvider
import com.superdash.voice.wake.WakeWordModel
import kotlinx.coroutines.flow.Flow

/**
 * App-owned [VoiceSettings] backed by [KeyValueStore].
 *
 * Reuses the legacy DataStore keys and defaults. Provider, wake-word, and
 * response-mode setters normalize through the feature's own enum factories,
 * matching the historical `SettingsRepositoryCommands` behavior.
 */
internal class SettingsRepositoryVoiceSettings(
    private val store: KeyValueStore,
) : VoiceSettings {
    override val enabled: Flow<Boolean> = store.observe(ENABLED)

    override val activeWakeWord: Flow<String> = store.observe(ACTIVE_WAKE_WORD)

    override val assistProvider: Flow<String> = store.observe(ASSIST_PROVIDER)

    override val primarySttProvider: Flow<String> = store.observe(PRIMARY_STT)

    override val secondarySttProvider: Flow<String> = store.observe(SECONDARY_STT)

    override val selectedSttModelId: Flow<String> = store.observe(SELECTED_STT_MODEL_ID)

    override val selectedIntentEmbeddingModelId: Flow<String> = store.observe(SELECTED_INTENT_EMBEDDING_MODEL_ID)

    override val localIntentRecognizerEnabled: Flow<Boolean> = store.observe(LOCAL_INTENT_RECOGNIZER_ENABLED)

    override val responseMode: Flow<String> = store.observe(RESPONSE_MODE)

    override val commandRecordingEnabled: Flow<Boolean> = store.observe(COMMAND_RECORDING_ENABLED)

    override val commandRecordingRetention: Flow<Int> = store.observe(COMMAND_RECORDING_RETENTION)

    override val vadSilenceMs: Flow<Int> = store.observe(VAD_SILENCE_MS)

    override suspend fun setEnabled(value: Boolean) = store.write(ENABLED, value)

    override suspend fun setActiveWakeWord(value: String) {
        if (WakeWordModel.find(value) != null) {
            store.write(ACTIVE_WAKE_WORD, value)
        }
    }

    override suspend fun setAssistProvider(value: String) = store.write(ASSIST_PROVIDER, value)

    override suspend fun setPrimarySttProvider(value: String) = store.write(PRIMARY_STT, value)

    override suspend fun setSecondarySttProvider(value: String) = store.write(SECONDARY_STT, value)

    override suspend fun setSelectedSttModelId(value: String) = store.write(SELECTED_STT_MODEL_ID, value)

    override suspend fun setSelectedIntentEmbeddingModelId(value: String) =
        store.write(SELECTED_INTENT_EMBEDDING_MODEL_ID, value)

    override suspend fun setLocalIntentRecognizerEnabled(value: Boolean) =
        store.write(LOCAL_INTENT_RECOGNIZER_ENABLED, value)

    override suspend fun setResponseMode(value: String) = store.write(RESPONSE_MODE, value)

    override suspend fun setCommandRecordingEnabled(value: Boolean) = store.write(COMMAND_RECORDING_ENABLED, value)

    override suspend fun setCommandRecordingRetention(value: Int) = store.write(COMMAND_RECORDING_RETENTION, value)

    override suspend fun setVadSilenceMs(value: Int) = store.write(VAD_SILENCE_MS, value)

    private companion object {
        val ENABLED = Setting(key = "voice_enabled", default = false)
        val ACTIVE_WAKE_WORD = Setting(key = "active_wake_word", default = WakeWordModel.DEFAULT_ID)

        // TODO 2027-Q1: drop sherpa→moonshine migration shim once existing installs have rotated
        val SHERPA_TO_MOONSHINE: (String) -> String = { value ->
            if (value == "sherpa" || value == "sherpa_stt_ha") {
                VoiceSttProvider.Moonshine.key
            } else {
                value
            }
        }
        val ASSIST_PROVIDER =
            Setting(
                key = "voice_assist_provider",
                default = VoiceSttProvider.HaAssist.key,
                read = SHERPA_TO_MOONSHINE,
                write = { VoiceSttProvider.fromKey(SHERPA_TO_MOONSHINE(it)).key },
            )
        val PRIMARY_STT =
            Setting(
                key = "primary_stt_provider",
                default = VoiceSttProvider.HaAssist.key,
                read = SHERPA_TO_MOONSHINE,
                write = { VoiceSttProvider.fromKey(SHERPA_TO_MOONSHINE(it)).key },
            )
        val SECONDARY_STT =
            Setting(
                key = "secondary_stt_provider",
                default = VoiceSttProvider.None.key,
                read = SHERPA_TO_MOONSHINE,
                write = { VoiceSttProvider.fromKey(SHERPA_TO_MOONSHINE(it)).key },
            )
        val SELECTED_STT_MODEL_ID =
            Setting(key = "selected_stt_model_id", default = VoiceModelIds.DEFAULT_STT_MODEL_ID)
        val SELECTED_INTENT_EMBEDDING_MODEL_ID =
            Setting(
                key = "selected_intent_embedding_model_id",
                default = VoiceModelIds.DEFAULT_INTENT_EMBEDDING_MODEL_ID,
            )
        val LOCAL_INTENT_RECOGNIZER_ENABLED = Setting(key = "local_intent_recognizer_enabled", default = false)
        val RESPONSE_MODE =
            Setting(
                key = "voice_response_mode",
                default = VoiceResponseMode.Speak.key,
                write = { VoiceResponseMode.fromKey(it).key },
            )
        val COMMAND_RECORDING_ENABLED = Setting(key = "voice_command_recording_enabled", default = false)
        val COMMAND_RECORDING_RETENTION =
            Setting(
                key = "voice_command_recording_retention",
                default = 100,
                write = { it.coerceIn(0, 500) },
            )
        val VAD_SILENCE_MS =
            Setting(key = "vad_silence_ms", default = 500, write = { it.coerceIn(250, 2500) })
    }
}
