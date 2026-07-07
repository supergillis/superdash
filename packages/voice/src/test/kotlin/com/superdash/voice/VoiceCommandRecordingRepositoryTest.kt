package com.superdash.voice

import com.superdash.core.persistence.FileMutationRunner
import com.superdash.core.persistence.SerializedFileMutationRunner
import com.superdash.voice.recording.VoiceCommandRecording
import com.superdash.voice.recording.VoiceCommandRecordingMetadata
import com.superdash.voice.recording.VoiceCommandRecordingRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class VoiceCommandRecordingRepositoryTest {
    @Test
    fun `save reads retention at save time`() =
        runTest {
            var retention = 2
            val dir = Files.createTempDirectory("voice-recordings").toFile()
            val repository =
                VoiceCommandRecordingRepository(
                    rootDir = dir,
                    retentionCount = { retention },
                    mutationRunner = testFileMutationRunner(),
                )

            repository.save(testRecording("1"))
            repository.save(testRecording("2"))
            retention = 1
            repository.save(testRecording("3"))

            assertEquals(
                listOf("3.json"),
                dir.listFiles { file -> file.extension == "json" }!!.map { file -> file.name },
            )
            assertEquals(
                listOf("3.wav"),
                dir.listFiles { file -> file.extension == "wav" }!!.map { file -> file.name },
            )
        }

    @Test
    fun `retention keeps newest recordings by metadata timestamp`() =
        runTest {
            val dir = Files.createTempDirectory("voice-recordings-retention-timestamp").toFile()
            val repository =
                VoiceCommandRecordingRepository(
                    rootDir = dir,
                    retentionCount = { 2 },
                    mutationRunner = testFileMutationRunner(),
                )

            repository.save(testRecording(id = "zzz-old", createdAtEpochMs = 1L))
            repository.save(testRecording(id = "mmm-middle", createdAtEpochMs = 2L))
            repository.save(testRecording(id = "aaa-new", createdAtEpochMs = 3L))

            assertEquals(
                listOf("aaa-new.json", "mmm-middle.json"),
                dir.listFiles { file -> file.extension == "json" }!!.map { file -> file.name }.sorted(),
            )
        }

    @Test
    fun `clear prevents pending save from recreating old recordings`() =
        runTest {
            val dir = Files.createTempDirectory("voice-recording-clear").toFile()
            val mutationRunner = PausingFirstMutationRunner()
            val repository =
                VoiceCommandRecordingRepository(
                    rootDir = dir,
                    retentionCount = { 100 },
                    mutationRunner = mutationRunner,
                )

            val saveJob = async { repository.save(testRecording("1")) }
            mutationRunner.firstMutationQueued.await()
            repository.clear()
            mutationRunner.releaseFirstMutation.complete(Unit)
            saveJob.await()

            assertEquals(emptyList<String>(), dir.listFiles().orEmpty().map { file -> file.name })
        }

    @Test
    fun `clear generation prevents save that starts after clear`() =
        runTest {
            val dir = Files.createTempDirectory("voice-recording-generation").toFile()
            val repository =
                VoiceCommandRecordingRepository(
                    rootDir = dir,
                    retentionCount = { 100 },
                    mutationRunner = testFileMutationRunner(),
                )
            val generation = repository.currentClearGeneration()

            repository.clear()
            repository.save(testRecording("1"), generation)

            assertEquals(emptyList<String>(), dir.listFiles().orEmpty().map { file -> file.name })
        }

    private fun testFileMutationRunner(): FileMutationRunner =
        SerializedFileMutationRunner(dispatcher = Dispatchers.Unconfined)

    private fun testRecording(
        id: String,
        createdAtEpochMs: Long = id.toLongOrNull() ?: 0L,
    ): VoiceCommandRecording =
        VoiceCommandRecording(
            metadata =
                VoiceCommandRecordingMetadata(
                    id = id,
                    createdAtEpochMs = createdAtEpochMs,
                    wakeWord = "hey_jarvis",
                    primaryProvider = "moonshine",
                    secondaryProvider = "none",
                    expectedText = "turn on desk lights",
                    transcript = "turn on desk lights",
                    finalState = "Completed",
                ),
            frames = listOf(shortArrayOf(1, 2, 3)),
        )

    private class PausingFirstMutationRunner : FileMutationRunner {
        val firstMutationQueued = CompletableDeferred<Unit>()
        val releaseFirstMutation = CompletableDeferred<Unit>()
        private var mutationCount = 0

        override suspend fun <T> mutate(block: suspend () -> T): T {
            mutationCount += 1
            if (mutationCount == 1) {
                firstMutationQueued.complete(Unit)
                releaseFirstMutation.await()
            }
            return block()
        }
    }
}
