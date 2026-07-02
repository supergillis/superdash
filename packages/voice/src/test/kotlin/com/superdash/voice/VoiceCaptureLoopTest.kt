package com.superdash.voice

import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.audio.AudioFrameHub
import com.superdash.voice.audio.withPreRoll
import com.superdash.voice.pipeline.VoiceCaptureLoop
import com.superdash.voice.pipeline.VoicePipelineCoordinator
import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderRunEvent
import com.superdash.voice.pipeline.VoiceProviderRunner
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunId
import com.superdash.voice.testing.NoopTts
import com.superdash.voice.wake.WakeEvent
import com.superdash.voice.wake.WakeWordDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCaptureLoopTest {
    @Test
    fun `pre roll audio is emitted before live audio`() =
        runTest {
            val output =
                flowOf(frame(3))
                    .withPreRoll(listOf(frame(1), frame(2)))
                    .toList()
                    .map { it.first().toInt() }

            assertEquals(listOf(1, 2, 3), output)
        }

    @Test
    fun `command stream buffers live frames before collection starts`() =
        runTest {
            val hub = AudioFrameHub(capacity = 4, streamScope = this)
            hub.publish(frame(1))
            hub.publish(frame(2))

            val commandAudio = hub.openStream()
            hub.publish(frame(3))
            hub.publish(frame(4))

            val output = commandAudio.audio.takeFrameValues(4)

            assertEquals(2, commandAudio.preRollFrameCount)
            assertEquals(listOf(1, 2, 3, 4), output)
        }

    @Test
    fun `hub publish ignores empty restart sentinel`() =
        runTest {
            val hub = AudioFrameHub(capacity = 4, streamScope = this)
            hub.publish(frame(1))
            hub.publish(ShortArray(0))
            hub.publish(frame(2))

            val stream = hub.openStream()

            assertEquals("sentinel is not buffered as pre-roll", 2, stream.preRollFrameCount)
        }

    @Test
    fun `provider missing closes uncollected command stream`() =
        runTest {
            var hub: AudioFrameHub? = null
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        VoiceProviderRunner { _, _ ->
                            flow {
                                emit(
                                    VoiceProviderRunEvent.Action(
                                        VoiceActionEvent.Error("voice-provider-missing", "missing"),
                                    ),
                                )
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus = KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            val loop =
                VoiceCaptureLoop(
                    source = { finiteThenSuspendingFrames() },
                    activeWakeWord = flowOf("hey_jarvis"),
                    vadSilenceMs = flowOf(500),
                    coordinator = coordinator,
                    runnerFactory = { FakeWakeWordDetector(WakeEvent("hey_jarvis")) },
                    createRunContext = { testRunContext("run-missing-provider") },
                    audioFrameHubFactory = { scope, capacity ->
                        AudioFrameHub(
                            capacity = capacity,
                            streamScope = scope,
                            uncollectedStreamTimeoutMs = 1_000,
                        ).also { createdHub ->
                            hub = createdHub
                        }
                    },
                )

            val job = launch { loop.run() }
            runCurrent()
            val commandAudioHub = requireNotNull(hub)

            assertEquals(1, commandAudioHub.subscriberCount())

            advanceTimeBy(1_000)
            runCurrent()

            assertEquals(0, commandAudioHub.subscriberCount())

            job.cancel()
        }

    @Test
    fun `wake detector observes restart sentinel even when audio stalls`() =
        runTest {
            // Bug guard: the wake-word RESTART_SENTINEL (zero-length ShortArray) is the
            // only signal that resets MicroWakeWordRunner streaming state across a mic
            // restart. If sharedAudio drops it under producer pressure, stale streaming
            // state leaks across the restart. This test stalls the wake-detector path,
            // floods many frames, emits the sentinel, then floods many more frames.
            val observedFrames = mutableListOf<Int>()
            val detector =
                object : WakeWordDetector {
                    override fun detect(audio: Flow<ShortArray>): Flow<WakeEvent> =
                        flow {
                            // Consumer is much slower than the producer: each frame
                            // costs 10 ms of virtual time. Under DROP_OLDEST this lets
                            // the producer race ahead and overwrite the sentinel; under
                            // SUSPEND emit() back-pressures so the sentinel survives.
                            audio.collect { samples ->
                                observedFrames +=
                                    if (samples.isEmpty()) {
                                        0
                                    } else {
                                        samples.first().toInt()
                                    }
                                delay(10)
                            }
                        }

                    override fun close() {}
                }

            val source =
                flow {
                    // Yield once so the consumer launch can subscribe to sharedAudio
                    // before the first emit (replay=0 means pre-subscription items are
                    // dropped regardless of overflow policy).
                    delay(1)
                    // Flood past the 64-slot buffer, emit the restart sentinel, then
                    // flood again so the sentinel becomes oldest under DROP_OLDEST.
                    repeat(256) {
                        emit(ShortArray(160) { 1 })
                    }
                    emit(ShortArray(0))
                    repeat(256) {
                        emit(ShortArray(160) { 2 })
                    }
                }

            val loop =
                VoiceCaptureLoop(
                    source = { source },
                    activeWakeWord = flowOf("hey_jarvis"),
                    vadSilenceMs = flowOf(500),
                    coordinator =
                        VoicePipelineCoordinator(
                            voiceProviderRunner = VoiceProviderRunner { _, _ -> flow {} },
                            ttsPlayer = NoopTts,
                            bus = KioskEventBus(),
                            dispatcher = StandardTestDispatcher(testScheduler),
                        ),
                    runnerFactory = { detector },
                    createRunContext = { testRunContext("run-sentinel") },
                )

            val job = launch { loop.run() }
            advanceUntilIdle()
            job.cancel()

            assertTrue(
                "wake detector must observe the restart sentinel (zero-length frame) " +
                    "even when consumer is slower than producer; observed=$observedFrames",
                observedFrames.contains(0),
            )
        }

    @Test
    fun `capture loop passes run context to transform and coordinator`() =
        runTest {
            val expectedContext = testRunContext("run-1")
            val contexts = mutableListOf<VoiceRunContext>()
            val coordinator =
                VoicePipelineCoordinator(
                    voiceProviderRunner =
                        VoiceProviderRunner { _, audio ->
                            flow {
                                audio.take(10).collect { }
                                emit(
                                    VoiceProviderRunEvent.Action(
                                        VoiceActionEvent.ActionComplete("turn on desk lights", buildJsonObject {}),
                                    ),
                                )
                            }
                        },
                    ttsPlayer = NoopTts,
                    bus = KioskEventBus(),
                    dispatcher = StandardTestDispatcher(testScheduler),
                )
            val loop =
                VoiceCaptureLoop(
                    source = { syntheticWakeAndCommandFrames() },
                    activeWakeWord = flowOf("hey_jarvis"),
                    vadSilenceMs = flowOf(500),
                    coordinator = coordinator,
                    runnerFactory = { FakeWakeWordDetector(WakeEvent("hey_jarvis")) },
                    createRunContext = { expectedContext },
                    commandAudioTransform = { context, audio ->
                        contexts += context
                        audio
                    },
                )

            val job = launch { loop.run() }
            advanceUntilIdle()
            job.cancel()

            assertEquals(listOf(expectedContext), contexts)
        }

    private companion object {
        private fun frame(value: Short): ShortArray = ShortArray(160) { value }

        private suspend fun Flow<ShortArray>.takeFrameValues(count: Int): List<Int> =
            take(count).toList().map { it.first().toInt() }

        private fun syntheticWakeAndCommandFrames(): Flow<ShortArray> =
            flow {
                repeat(400) {
                    emit(ShortArray(160) { 1 })
                }
            }

        private fun finiteThenSuspendingFrames(): Flow<ShortArray> =
            flow {
                repeat(20) {
                    emit(ShortArray(160) { 1 })
                }
                awaitCancellation()
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
    }

    private class FakeWakeWordDetector(
        private val event: WakeEvent,
    ) : WakeWordDetector {
        override fun detect(audio: Flow<ShortArray>): Flow<WakeEvent> =
            flow {
                emit(event)
            }

        override fun close() {}
    }
}
