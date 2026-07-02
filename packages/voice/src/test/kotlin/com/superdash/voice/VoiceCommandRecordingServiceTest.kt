package com.superdash.voice

import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.pipeline.VoiceRunResult
import com.superdash.voice.pipeline.VoiceRunTerminalState
import com.superdash.voice.recording.VoiceCommandRecording
import com.superdash.voice.recording.VoiceCommandRecordingRepository
import com.superdash.voice.recording.VoiceCommandRecordingService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalForInheritanceCoroutinesApi::class, InternalCoroutinesApi::class)
class VoiceCommandRecordingServiceTest {
    @Test
    fun `save job rethrows cancellation while waiting for run result`() =
        runTest {
            val saved = mutableListOf<VoiceCommandRecording>()
            val collectionStarted = CompletableDeferred<Unit>()
            val throwCancellation = CompletableDeferred<Unit>()
            val results =
                object : SharedFlow<VoiceRunResult> {
                    override val replayCache: List<VoiceRunResult> = emptyList()

                    override suspend fun collect(collector: FlowCollector<VoiceRunResult>): Nothing {
                        collectionStarted.complete(Unit)
                        throwCancellation.await()
                        throw CancellationException("synthetic cancellation")
                    }
                }
            val serviceJob = SupervisorJob()
            val serviceScope = CoroutineScope(serviceJob + StandardTestDispatcher(testScheduler))
            val service =
                VoiceCommandRecordingService(
                    enabled = { true },
                    runResults = results,
                    saveRecording = { recording, _ -> saved += recording },
                    scope = serviceScope,
                )
            val context = testRunContext("first")
            val finishAudio = CompletableDeferred<Unit>()
            val audio =
                flow {
                    emit(shortArrayOf(1))
                    finishAudio.await()
                }

            val collectJob = launch { service.record(context, audio).toList() }
            collectionStarted.await()
            val saveJob = serviceJob.children.single()
            throwCancellation.complete(Unit)
            advanceUntilIdle()

            assertTrue(saveJob.isCancelled)
            assertEquals(emptyList<VoiceCommandRecording>(), saved)

            finishAudio.complete(Unit)
            collectJob.join()
            serviceJob.cancelAndJoin()
        }

    @Test
    fun `recording saves only matching run result`() =
        runTest {
            val saved = mutableListOf<VoiceCommandRecording>()
            val results = MutableSharedFlow<VoiceRunResult>(extraBufferCapacity = 2)
            val service =
                VoiceCommandRecordingService(
                    enabled = { true },
                    runResults = results,
                    saveRecording = { recording, _ -> saved += recording },
                    scope = this,
                )
            val firstContext = testRunContext("first")
            val secondContext = testRunContext("second")

            val collectJob =
                launch {
                    service.record(firstContext, listOf(shortArrayOf(1)).asFlow()).toList()
                }
            collectJob.join()
            results.emit(testRunResult(secondContext, "wrong"))
            results.emit(testRunResult(firstContext, "right"))
            advanceUntilIdle()

            assertEquals(listOf("right"), saved.map { recording -> recording.metadata.transcript })
        }

    @Test
    fun `recording waits for audio to finish before saving matching result`() =
        runTest {
            val saved = mutableListOf<VoiceCommandRecording>()
            val results = MutableSharedFlow<VoiceRunResult>(extraBufferCapacity = 1)
            val service =
                VoiceCommandRecordingService(
                    enabled = { true },
                    runResults = results,
                    saveRecording = { recording, _ -> saved += recording },
                    scope = this,
                )
            val context = testRunContext("first")
            val firstFrameForwarded = CompletableDeferred<Unit>()
            val finishAudio = CompletableDeferred<Unit>()
            val audio =
                flow {
                    emit(shortArrayOf(1))
                    firstFrameForwarded.complete(Unit)
                    finishAudio.await()
                    emit(shortArrayOf(2))
                }

            val collectJob = launch { service.record(context, audio).toList() }
            firstFrameForwarded.await()
            results.emit(testRunResult(context, "right"))
            advanceUntilIdle()

            assertEquals(emptyList<String>(), saved.map { recording -> recording.metadata.transcript })

            finishAudio.complete(Unit)
            collectJob.join()
            advanceUntilIdle()

            assertEquals(listOf("right"), saved.map { recording -> recording.metadata.transcript })
        }

    @Test
    fun `recording skips cancelled run result`() =
        runTest {
            val saved = mutableListOf<VoiceCommandRecording>()
            val results = MutableSharedFlow<VoiceRunResult>(extraBufferCapacity = 1)
            val service =
                VoiceCommandRecordingService(
                    enabled = { true },
                    runResults = results,
                    saveRecording = { recording, _ -> saved += recording },
                    scope = this,
                )
            val context = testRunContext("first")

            service.record(context, listOf(shortArrayOf(1)).asFlow()).toList()
            results.emit(
                VoiceRunResult(
                    context = context,
                    terminalState = VoiceRunTerminalState.Cancelled,
                    providerTrace = emptyList(),
                ),
            )
            advanceUntilIdle()

            assertEquals(emptyList<String>(), saved.map { recording -> recording.metadata.transcript })
        }

    @Test
    fun `recording saves result after old timeout window`() =
        runTest {
            val saved = mutableListOf<VoiceCommandRecording>()
            val results = MutableSharedFlow<VoiceRunResult>(extraBufferCapacity = 1)
            val service =
                VoiceCommandRecordingService(
                    enabled = { true },
                    runResults = results,
                    saveRecording = { recording, _ -> saved += recording },
                    scope = this,
                )
            val context = testRunContext("first")

            service.record(context, listOf(shortArrayOf(1)).asFlow()).toList()
            advanceTimeBy(16_000L)
            results.emit(testRunResult(context, "late but valid"))
            advanceUntilIdle()

            assertEquals(listOf("late but valid"), saved.map { recording -> recording.metadata.transcript })
        }

    @Test
    fun `recording cancelled before audio completion does not save later result`() =
        runTest {
            val saved = mutableListOf<VoiceCommandRecording>()
            val results = MutableSharedFlow<VoiceRunResult>(extraBufferCapacity = 1)
            val firstFrameForwarded = CompletableDeferred<Unit>()
            val finishAudio = CompletableDeferred<Unit>()
            val service =
                VoiceCommandRecordingService(
                    enabled = { true },
                    runResults = results,
                    saveRecording = { recording, _ -> saved += recording },
                    scope = this,
                )
            val context = testRunContext("first")
            val audio =
                flow {
                    emit(shortArrayOf(1))
                    firstFrameForwarded.complete(Unit)
                    finishAudio.await()
                }

            val collectJob = launch { service.record(context, audio).toList() }
            firstFrameForwarded.await()
            collectJob.cancel()
            results.emit(testRunResult(context, "should not save"))
            advanceUntilIdle()

            assertEquals(emptyList<String>(), saved.map { recording -> recording.metadata.transcript })
        }

    @Test
    fun `recording clear prevents active service save`() =
        runTest {
            val dir = Files.createTempDirectory("voice-recording-service-clear").toFile()
            val repository =
                VoiceCommandRecordingRepository(
                    rootDir = dir,
                    retentionCount = { 100 },
                )
            val results = MutableSharedFlow<VoiceRunResult>(extraBufferCapacity = 1)
            val finishAudio = CompletableDeferred<Unit>()
            val service =
                VoiceCommandRecordingService(
                    enabled = { true },
                    runResults = results,
                    currentClearGeneration = { repository.currentClearGeneration() },
                    saveRecording = { recording, generation -> repository.save(recording, generation) },
                    scope = this,
                )
            val context = testRunContext("first")
            val audio =
                flow {
                    emit(shortArrayOf(1))
                    finishAudio.await()
                }

            val collectJob = launch { service.record(context, audio).toList() }
            repository.clear()
            results.emit(testRunResult(context, "should not save"))
            finishAudio.complete(Unit)
            collectJob.join()
            advanceUntilIdle()

            assertEquals(emptyList<String>(), dir.listFiles().orEmpty().map { file -> file.name })
        }

    @Test
    fun `recording keeps private frame copy`() =
        runTest {
            val saved = mutableListOf<VoiceCommandRecording>()
            val results = MutableSharedFlow<VoiceRunResult>(extraBufferCapacity = 1)
            val service =
                VoiceCommandRecordingService(
                    enabled = { true },
                    runResults = results,
                    saveRecording = { recording, _ -> saved += recording },
                    scope = this,
                )
            val context = testRunContext("first")

            service
                .record(context, listOf(shortArrayOf(1)).asFlow())
                .onEach { frame -> frame[0] = 99 }
                .toList()
            results.emit(testRunResult(context, "right"))
            advanceUntilIdle()

            assertEquals(
                1,
                saved
                    .single()
                    .frames
                    .single()
                    .single()
                    .toInt(),
            )
        }

    private fun testRunContext(id: String): VoiceRunContext =
        VoiceRunContext(
            id = VoiceRunId(id),
            wakeWord = "hey_jarvis",
            startedAtEpochMs = 1_000L,
            providerSelection =
                VoiceProviderSelection(
                    primary = VoiceProviderIdentity("moonshine", "moonshine-tiny-en"),
                    secondary = VoiceProviderIdentity("ha_assist", null),
                ),
        )

    private fun testRunResult(
        context: VoiceRunContext,
        transcript: String,
    ): VoiceRunResult =
        VoiceRunResult(
            context = context,
            terminalState = VoiceRunTerminalState.Completed(transcript),
            providerTrace = emptyList(),
        )
}
