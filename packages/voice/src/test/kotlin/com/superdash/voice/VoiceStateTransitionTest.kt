package com.superdash.voice

import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.executors.TtsPlay
import com.superdash.voice.action.recognizedWordsFromText
import com.superdash.voice.pipeline.VoicePipelineCoordinator
import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderRunEvent
import com.superdash.voice.pipeline.VoiceProviderRunner
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceResponseMode
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.pipeline.VoiceState
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.RecognizedWord
import com.superdash.voice.testing.NoopTts
import com.superdash.voice.wake.WakeWordModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceStateTransitionTest {
    @Test fun `idle on construction`() =
        runTest {
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner = eventRunner(MutableSharedFlow()),
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )
            assertEquals(VoiceState.Idle, coordinator.state.value)
        }

    @Test fun `wake fired transitions Idle → WakeFired → Recording`() =
        runTest {
            val events = MutableSharedFlow<VoiceActionEvent>(extraBufferCapacity = 8)
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner = eventRunner(events),
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )
            coordinator.onWake(testRunContext(), audio = emptyFlow())
            advanceTimeBy(250) // past the 200ms WakeFired blip into Recording
            runCurrent()
            assertTrue(
                "Expected WakeFired or Recording, got " + coordinator.state.value,
                coordinator.state.value is VoiceState.WakeFired ||
                    coordinator.state.value is VoiceState.Recording,
            )
        }

    @Test fun `assist runner starts immediately after wake`() =
        runTest {
            var assistRunnerStarted = false
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        providerRunner { _ ->
                            assistRunnerStarted = true
                            MutableSharedFlow()
                        },
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )

            coordinator.onWake(testRunContext(), audio = emptyFlow())
            runCurrent()

            assertTrue("Assist should start without waiting for the wake blip", assistRunnerStarted)
            assertFalse("Wake blip should still be visible", coordinator.state.value is VoiceState.Recording)
        }

    @Test fun `stt-end transitions Recording → Processing with transcript`() =
        runTest {
            val events = MutableSharedFlow<VoiceActionEvent>(extraBufferCapacity = 8)
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner = eventRunner(events),
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )
            coordinator.onWake(testRunContext(), audio = emptyFlow())
            runCurrent()
            events.emit(
                VoiceActionEvent.Recognition(
                    RecognitionUpdate.Final(words = recognizedWordsFromText("what time is it")),
                ),
            )
            runCurrent()
            val state = coordinator.state.value
            assertTrue(
                "Expected Processing(\"what time is it\"), got " + state,
                state is VoiceState.Processing && state.transcript == "what time is it",
            )
        }

    @Test fun `partial recognition updates Recording transcript`() =
        runTest {
            val events = MutableSharedFlow<VoiceActionEvent>(extraBufferCapacity = 8)
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner = eventRunner(events),
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )

            coordinator.onWake(testRunContext(), audio = emptyFlow())
            runCurrent()
            events.emit(
                VoiceActionEvent.Recognition(
                    RecognitionUpdate.Partial(
                        words =
                            listOf(
                                RecognizedWord(text = "turn", isFinal = true),
                                RecognizedWord(text = "on", isFinal = false),
                            ),
                    ),
                ),
            )
            runCurrent()

            assertEquals(VoiceState.Recording(partialTranscript = "turn on"), coordinator.state.value)
        }

    @Test fun `tts-end transitions Processing → Speaking carrying transcript`() =
        runTest {
            val events = MutableSharedFlow<VoiceActionEvent>(extraBufferCapacity = 8)
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner = eventRunner(events),
                    ttsPlayer = SuspendingTts(),
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )
            coordinator.onWake(testRunContext(), audio = emptyFlow())
            runCurrent()
            events.emit(
                VoiceActionEvent.Recognition(
                    RecognitionUpdate.Final(words = recognizedWordsFromText("hi")),
                ),
            )
            events.emit(VoiceActionEvent.TtsReady("/api/tts_proxy/abc.mp3"))
            runCurrent()
            val state = coordinator.state.value
            assertTrue(
                "Expected Speaking(ttsUrl, transcript=\"hi\"), got " + state,
                state is VoiceState.Speaking &&
                    state.ttsUrl == "/api/tts_proxy/abc.mp3" &&
                    state.transcript == "hi",
            )
        }

    @Test fun `intent-end records action completion with transcript and response`() =
        runTest {
            val events = MutableSharedFlow<VoiceActionEvent>(extraBufferCapacity = 8)
            val response =
                buildJsonObject {
                    put("conversation_id", JsonPrimitive("abc"))
                    put("response_type", JsonPrimitive("action_done"))
                }
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner = eventRunner(events),
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )

            coordinator.onWake(testRunContext(), audio = emptyFlow())
            runCurrent()
            events.emit(
                VoiceActionEvent.Recognition(
                    RecognitionUpdate.Final(words = recognizedWordsFromText("turn on kitchen")),
                ),
            )
            events.emit(VoiceActionEvent.ActionComplete(response = response))
            runCurrent()

            assertEquals(VoiceState.ActionComplete("turn on kitchen", response), coordinator.state.value)
        }

    @Test fun `error event transitions to Failed`() =
        runTest {
            val events = MutableSharedFlow<VoiceActionEvent>(extraBufferCapacity = 8)
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner = eventRunner(events),
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )
            coordinator.onWake(testRunContext(), audio = emptyFlow())
            runCurrent()
            events.emit(VoiceActionEvent.Error("pipeline_not_found", "no pipeline"))
            runCurrent() // process the emit but do NOT advance past the 3s Failed→Idle delay
            val state = coordinator.state.value
            assertTrue(
                "Expected Failed, got " + state,
                state is VoiceState.Failed && state.reason.contains("pipeline_not_found"),
            )
        }

    @Test fun `failed returns to Idle after 3s`() =
        runTest {
            val events = MutableSharedFlow<VoiceActionEvent>(extraBufferCapacity = 8)
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner = eventRunner(events),
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )
            coordinator.onWake(testRunContext(), audio = emptyFlow())
            runCurrent()
            events.emit(VoiceActionEvent.Error("oops", "x"))
            advanceTimeBy(3_500) // past the 3s Failed→Idle delay
            runCurrent()
            assertEquals(VoiceState.Idle, coordinator.state.value)
        }

    @Test fun `voice e2e records HA action completion without waiting for tts`() =
        runTest {
            val audioFrames =
                kotlinx.coroutines.flow.flow {
                    emit(ShortArray(160) { 1 })
                    emit(ShortArray(160) { 2 })
                }
            val seenSamples = mutableListOf<ShortArray>()
            val intentResponse: JsonObject =
                buildJsonObject {
                    put("action", JsonPrimitive("light.turn_on"))
                    put("target", JsonPrimitive("light.kitchen"))
                }
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        providerRunner { audio ->
                            flow {
                                audio.collect { seenSamples += it }
                                emit(
                                    VoiceActionEvent.Recognition(
                                        RecognitionUpdate.Final(words = recognizedWordsFromText("turn on kitchen")),
                                    ),
                                )
                                emit(VoiceActionEvent.ActionComplete(response = intentResponse))
                                kotlinx.coroutines.awaitCancellation()
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )

            coordinator.onWake(testRunContext(), audioFrames)
            runCurrent()

            assertEquals(2, seenSamples.size)
            assertEquals(VoiceState.ActionComplete("turn on kitchen", intentResponse), coordinator.state.value)
            coordinator.stopAll()
        }

    @Test fun `silent response mode returns Idle after action completes without TTS`() =
        runTest {
            val response = buildJsonObject { put("response_type", JsonPrimitive("action_done")) }
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        providerRunner { _ ->
                            flow {
                                emit(
                                    VoiceActionEvent.Recognition(
                                        RecognitionUpdate.Final(words = recognizedWordsFromText("turn on kitchen")),
                                    ),
                                )
                                emit(VoiceActionEvent.ActionComplete(response = response))
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    responseModes = flowOf(VoiceResponseMode.Silent),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )

            coordinator.onWake(testRunContext(), audio = emptyFlow())
            runCurrent()

            assertEquals(VoiceState.Idle, coordinator.state.value)
        }

    @Test fun `visual response mode holds ActionComplete when TTS never arrives`() =
        runTest {
            val response = buildJsonObject { put("response_type", JsonPrimitive("action_done")) }
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        providerRunner { _ ->
                            flow {
                                emit(
                                    VoiceActionEvent.Recognition(
                                        RecognitionUpdate.Final(words = recognizedWordsFromText("turn on kitchen")),
                                    ),
                                )
                                emit(VoiceActionEvent.ActionComplete(response = response))
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus =
                        com.superdash.kiosk.bus
                            .KioskEventBus(),
                    responseModes = flowOf(VoiceResponseMode.Visual),
                    dispatcher = StandardTestDispatcher(testScheduler),
                    assistRunTimeoutMs = Long.MAX_VALUE,
                )

            coordinator.onWake(testRunContext(), audio = emptyFlow())
            runCurrent()

            assertEquals(VoiceState.ActionComplete("turn on kitchen", response), coordinator.state.value)
            advanceTimeBy(3_000)
            runCurrent()
            assertEquals(VoiceState.Idle, coordinator.state.value)
        }

    private fun emptyFlow(): Flow<ShortArray> = MutableSharedFlow<ShortArray>().map { it }

    private fun eventRunner(events: Flow<VoiceActionEvent>): VoiceProviderRunner =
        providerRunner { events }

    private fun providerRunner(provider: (Flow<ShortArray>) -> Flow<VoiceActionEvent>): VoiceProviderRunner =
        VoiceProviderRunner { _, audio ->
            provider(audio).map { event -> VoiceProviderRunEvent.Action(event) }
        }

    private fun testRunContext(): VoiceRunContext =
        VoiceRunContext(
            id = VoiceRunId.new(),
            wakeWord = WakeWordModel.DEFAULT_ID,
            startedAtEpochMs = 1_000L,
            providerSelection =
                VoiceProviderSelection(
                    primary = VoiceProviderIdentity("test", null),
                    secondary = null,
                ),
        )

    private class SuspendingTts : TtsPlay {
        override suspend fun play(url: String) {
            kotlinx.coroutines.awaitCancellation() // never completes; test only checks Speaking is entered
        }

        override fun stop() {}
    }
}
