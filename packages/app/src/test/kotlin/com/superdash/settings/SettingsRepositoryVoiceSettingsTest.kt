package com.superdash.settings

import com.superdash.core.persistence.InMemoryKeyValueStore
import com.superdash.voice.models.VoiceModelIds
import com.superdash.voice.pipeline.VoiceResponseMode
import com.superdash.voice.pipeline.VoiceSttProvider
import com.superdash.voice.wake.WakeWordModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsRepositoryVoiceSettingsTest {
    @Test
    fun `enabled defaults to false`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            assertEquals(false, settings.enabled.first())
        }

    @Test
    fun `activeWakeWord defaults to WakeWordModel DEFAULT_ID`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            assertEquals(WakeWordModel.DEFAULT_ID, settings.activeWakeWord.first())
        }

    @Test
    fun `primary stt defaults to HaAssist key`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            assertEquals(VoiceSttProvider.HaAssist.key, settings.primarySttProvider.first())
        }

    @Test
    fun `secondary stt defaults to None key`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            assertEquals(VoiceSttProvider.None.key, settings.secondarySttProvider.first())
        }

    @Test
    fun `selectedSttModelId defaults to VoiceModelIds DEFAULT_STT_MODEL_ID`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            assertEquals(VoiceModelIds.DEFAULT_STT_MODEL_ID, settings.selectedSttModelId.first())
        }

    @Test
    fun `selectedIntentEmbeddingModelId defaults to default intent embedding id`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            assertEquals(
                VoiceModelIds.DEFAULT_INTENT_EMBEDDING_MODEL_ID,
                settings.selectedIntentEmbeddingModelId.first(),
            )
        }

    @Test
    fun `responseMode defaults to Speak key`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            assertEquals(VoiceResponseMode.Speak.key, settings.responseMode.first())
        }

    @Test
    fun `vadSilenceMs coerces below 250 to 250`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            settings.setVadSilenceMs(100)
            assertEquals(250, settings.vadSilenceMs.first())
        }

    @Test
    fun `vadSilenceMs coerces above 2500 to 2500`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            settings.setVadSilenceMs(9999)
            assertEquals(2500, settings.vadSilenceMs.first())
        }

    @Test
    fun `commandRecordingRetention coerces below zero to zero`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            settings.setCommandRecordingRetention(-1)
            assertEquals(0, settings.commandRecordingRetention.first())
        }

    @Test
    fun `commandRecordingRetention coerces above 500 to 500`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            settings.setCommandRecordingRetention(9999)
            assertEquals(500, settings.commandRecordingRetention.first())
        }

    @Test
    fun `setAssistProvider normalizes through fromKey`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            settings.setAssistProvider("garbage-not-a-real-key")
            assertEquals(VoiceSttProvider.fromKey("garbage-not-a-real-key").key, settings.assistProvider.first())
        }

    @Test
    fun `setActiveWakeWord ignores unknown values`() =
        runTest {
            val settings = SettingsRepositoryVoiceSettings(InMemoryKeyValueStore())
            val before = settings.activeWakeWord.first()
            settings.setActiveWakeWord("definitely-not-a-real-wake-word")
            assertEquals(before, settings.activeWakeWord.first())
        }
}
