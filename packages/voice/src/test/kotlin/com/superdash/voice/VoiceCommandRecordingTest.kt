package com.superdash.voice

import com.superdash.voice.recording.VoiceCommandRecording
import com.superdash.voice.recording.VoiceCommandRecordingMetadata
import com.superdash.voice.recording.VoiceCommandRecordingRepository
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class VoiceCommandRecordingTest {
    @Test
    fun `writer stores pcm16 mono wav with metadata`() =
        runTest {
            val dir = Files.createTempDirectory("voice-recording-test").toFile()
            val repository = VoiceCommandRecordingRepository(rootDir = dir, retentionCount = { 100 })
            val metadata =
                VoiceCommandRecordingMetadata(
                    id = "rec-1",
                    createdAtEpochMs = 1_700_000_000_000L,
                    wakeWord = "hey_jarvis",
                    primaryProvider = "moonshine",
                    secondaryProvider = "ha_assist",
                    expectedText = null,
                    transcript = "turn on kitchen lights",
                    finalState = "ActionComplete",
                )

            val saved =
                repository.save(
                    VoiceCommandRecording(
                        metadata = metadata,
                        frames = listOf(shortArrayOf(0, 1024, -1024)),
                    ),
                )

            assertTrue(saved.wavFile.name.endsWith(".wav"))
            assertEquals("RIFF", saved.wavFile.readBytes().decodeToString(0, 4))
            assertTrue(saved.metadataFile.readText().contains("\"transcript\":\"turn on kitchen lights\""))
            assertEquals(metadata, Json.decodeFromString<VoiceCommandRecordingMetadata>(saved.metadataFile.readText()))
        }

    @Test
    fun `retention keeps newest recordings`() =
        runTest {
            val dir = Files.createTempDirectory("voice-recording-retention").toFile()
            val repository = VoiceCommandRecordingRepository(rootDir = dir, retentionCount = { 2 })

            repeat(3) { index ->
                repository.save(
                    VoiceCommandRecording(
                        metadata =
                            VoiceCommandRecordingMetadata(
                                id = "rec-$index",
                                createdAtEpochMs = index.toLong(),
                                wakeWord = "hey_jarvis",
                                primaryProvider = "ha_assist",
                                secondaryProvider = "none",
                                expectedText = null,
                                transcript = "command $index",
                                finalState = "ActionComplete",
                            ),
                        frames = listOf(shortArrayOf(index.toShort())),
                    ),
                )
            }

            val metadataFiles =
                dir
                    .listFiles { file -> file.extension == "json" }!!
                    .map { file -> file.name }
                    .sorted()
            assertEquals(listOf("rec-1.json", "rec-2.json"), metadataFiles)
        }
}
