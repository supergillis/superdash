package com.superdash.voice

import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.recognizedWordsFromText
import com.superdash.voice.pipeline.VoicePipelineCoordinator
import com.superdash.voice.pipeline.VoiceProviderAttempt
import com.superdash.voice.pipeline.VoiceProviderAttemptResult
import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderRun
import com.superdash.voice.pipeline.VoiceProviderRunEvent
import com.superdash.voice.pipeline.VoiceProviderRunner
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.pipeline.VoiceRunTerminalState
import com.superdash.voice.pipeline.VoiceState
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.testing.NoopTts
import com.superdash.voice.wake.WakeWordModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoicePipelineCoordinatorTest {
    @Test
    fun `DoorbellRingStarted on bus cancels in-flight wake job`() =
        runTest {
            val bus = KioskEventBus()
            val coordinator = buildCoordinator(bus = bus, dispatcher = StandardTestDispatcher(testScheduler))
            coordinator.onWake(testRunContext("doorbell"), neverEndingAudio())
            advanceUntilIdle()
            bus.emit(KioskEvent.DoorbellRingStarted("a", 0L))
            advanceUntilIdle()
            assertEquals(VoiceState.Idle, coordinator.state.value)
        }

    @Test
    fun `cancelled run does not receive next run result`() =
        runTest {
            val firstContext = testRunContext("first")
            val secondContext = testRunContext("second")
            val provider = ControllableVoiceProviderRunner()
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner = provider,
                    ttsPlayer = NoopTts,
                    bus = KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            coordinator.onWake(firstContext, shortFramesFlow())
            runCurrent()
            assertEquals(firstContext.providerSelection, provider.startedSelections.receive())
            coordinator.onWake(secondContext, shortFramesFlow())
            runCurrent()
            assertEquals(secondContext.providerSelection, provider.startedSelections.receive())
            provider.completeNext("turn on desk lights")
            advanceUntilIdle()

            val results = coordinator.runResults.replayCache
            assertEquals(listOf(secondContext.id), results.map { result -> result.context.id })
            assertEquals("turn on desk lights", results.single().transcript)
        }

    @Test
    fun `speaking run result includes attempt emitted after tts event`() =
        runTest {
            val context = testRunContext("speaking")
            val attempt = testAttempt(context.providerSelection.primary)
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        VoiceProviderRunner { _, _ ->
                            flow {
                                emit(VoiceProviderRunEvent.Action(VoiceActionEvent.TtsReady("/api/tts_proxy/test.mp3")))
                                emit(VoiceProviderRunEvent.AttemptFinished(attempt))
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus = KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            coordinator.onWake(context, shortFramesFlow())
            advanceUntilIdle()

            val result = coordinator.runResults.replayCache.single()
            assertEquals(VoiceRunTerminalState.Speaking(""), result.terminalState)
            assertEquals(listOf(attempt), result.providerTrace)
        }

    @Test
    fun `stopAll while assist is running does not leave state Failed after Idle`() =
        runTest {
            // Reproduces the race: a provider error transitions to Failed via
            // failTransition, which schedules a delayed Failed→Idle clear on
            // the coordinator scope. stopAll explicitly sets Idle while the
            // delayed clear is still pending; without a generation guard, a
            // subsequent re-entrant code path on the cancelled job could
            // restore Failed.
            val context = testRunContext("racing")
            val errorGate = CompletableDeferred<Unit>()
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        VoiceProviderRunner { _, _ ->
                            flow {
                                errorGate.await()
                                emit(VoiceProviderRunEvent.Action(VoiceActionEvent.Error("provider", "failed")))
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus = KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )

            coordinator.onWake(context, shortFramesFlow())
            advanceUntilIdle()
            // stopAll resets to Idle and bumps generation; the cancelled job
            // may still resume from the errorGate suspension point.
            coordinator.stopAll()
            advanceUntilIdle()
            assertEquals(VoiceState.Idle, coordinator.state.value)
            errorGate.complete(Unit)
            advanceUntilIdle()
            // Even if the cancelled job's catch path runs, the generation
            // guard prevents failTransition from overwriting Idle with Failed.
            assertEquals(VoiceState.Idle, coordinator.state.value)
        }

    @Test
    fun `run result terminal state is derived from events not UI state`() =
        runTest {
            val context = testRunContext("eventderived")
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        VoiceProviderRunner { _, _ ->
                            flow {
                                emit(
                                    VoiceProviderRunEvent.Action(
                                        VoiceActionEvent.Recognition(
                                            RecognitionUpdate.Partial(
                                                words = recognizedWordsFromText("turn on"),
                                            ),
                                        ),
                                    ),
                                )
                                emit(
                                    VoiceProviderRunEvent.Action(
                                        VoiceActionEvent.Recognition(
                                            RecognitionUpdate.Final(
                                                words = recognizedWordsFromText("turn on lights"),
                                            ),
                                        ),
                                    ),
                                )
                                emit(
                                    VoiceProviderRunEvent.Action(
                                        VoiceActionEvent.ActionComplete(
                                            transcript = "turn on lights",
                                            response = buildJsonObject {},
                                        ),
                                    ),
                                )
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus = KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            coordinator.onWake(context, shortFramesFlow())
            advanceUntilIdle()

            val result = coordinator.runResults.replayCache.single()
            assertEquals(VoiceRunTerminalState.Completed("turn on lights"), result.terminalState)
        }

    @Test
    fun `failed run result includes attempt emitted before provider error`() =
        runTest {
            val context = testRunContext("failed")
            val attempt =
                testAttempt(
                    identity = context.providerSelection.primary,
                    result = VoiceProviderAttemptResult.Failed("provider: failed"),
                )
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        VoiceProviderRunner { _, _ ->
                            flow {
                                emit(VoiceProviderRunEvent.AttemptFinished(attempt))
                                emit(VoiceProviderRunEvent.Action(VoiceActionEvent.Error("provider", "failed")))
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus = KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )

            coordinator.onWake(context, shortFramesFlow())
            advanceUntilIdle()

            val result = coordinator.runResults.replayCache.single()
            assertEquals(VoiceRunTerminalState.Failed("provider: failed"), result.terminalState)
            assertEquals(listOf(attempt), result.providerTrace)
        }

    private fun buildCoordinator(
        bus: KioskEventBus,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): VoicePipelineCoordinator =
        VoicePipelineCoordinator(
            voiceProviderRunner =
                VoiceProviderRunner { _, _ ->
                    MutableSharedFlow<VoiceActionEvent>().asRunEvents()
                },
            ttsPlayer = NoopTts,
            bus = bus,
            dispatcher = dispatcher,
            assistRunTimeoutMs = Long.MAX_VALUE,
        )

    private fun neverEndingAudio(): Flow<ShortArray> =
        channelFlow { awaitCancellation() }

    private fun shortFramesFlow(): Flow<ShortArray> =
        flow {
            emit(shortArrayOf(1, 2, 3))
        }

    private fun Flow<VoiceActionEvent>.asRunEvents(): Flow<VoiceProviderRunEvent> =
        map { event -> VoiceProviderRunEvent.Action(event) }

    private fun testRunContext(id: String): VoiceRunContext =
        VoiceRunContext(
            id = VoiceRunId(id),
            wakeWord = WakeWordModel.DEFAULT_ID,
            startedAtEpochMs = 1_000L,
            providerSelection =
                VoiceProviderSelection(
                    primary = VoiceProviderIdentity(id, null),
                    secondary = VoiceProviderIdentity("ha_assist", null),
                ),
        )

    private fun testAttempt(
        identity: VoiceProviderIdentity,
        result: VoiceProviderAttemptResult = VoiceProviderAttemptResult.Completed(actionComplete = true),
    ): VoiceProviderAttempt =
        VoiceProviderAttempt(
            identity = identity,
            elapsedMs = 1L,
            result = result,
        )

    private class ControllableVoiceProviderRunner : VoiceProviderRunner {
        private val queuedRuns = Channel<VoiceProviderRun>(Channel.UNLIMITED)
        val startedSelections = Channel<VoiceProviderSelection>(Channel.UNLIMITED)

        override fun run(
            selection: VoiceProviderSelection,
            audio: Flow<ShortArray>,
        ): Flow<VoiceProviderRunEvent> =
            flow {
                startedSelections.send(selection)
                audio.toList()
                if (selection.primary.stableKey == "first") {
                    awaitCancellation()
                }
                val run = queuedRuns.receive()
                run.events.forEach { event -> emit(VoiceProviderRunEvent.Action(event)) }
                run.providerTrace.forEach { attempt -> emit(VoiceProviderRunEvent.AttemptFinished(attempt)) }
            }

        suspend fun completeNext(transcript: String) {
            queuedRuns.send(
                VoiceProviderRun(
                    events =
                        listOf(
                            VoiceActionEvent.Recognition(
                                RecognitionUpdate.Final(words = recognizedWordsFromText(transcript)),
                            ),
                            VoiceActionEvent.ActionComplete(transcript, buildJsonObject {}),
                        ),
                    providerTrace = emptyList(),
                ),
            )
        }
    }
}
