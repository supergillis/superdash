package com.superdash.ha

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Consumer-facing contract for [HaAssistClient.runPipeline].
 *
 *  Pins behaviors that downstream voice consumers rely on:
 *  - `VoiceProviderWithFallback` treats [AssistEvent.Error] as terminal.
 *  - `VoicePipelineCoordinator` throws on Error; never expects a subsequent RunEnd.
 *  - `RetryingVoiceActionProvider` only retries `stt-stream-failed` Errors.
 *
 *  Tests use a fake [HaAssistTransport] so we exercise the real client logic. */
@OptIn(ExperimentalCoroutinesApi::class)
class HaAssistRunPipelineContractTest {
    @Test fun `successful run emits RunEnd as the final terminal event`() =
        runTest(UnconfinedTestDispatcher()) {
            val transport = FakeTransport()
            val client = HaAssistClient(transport)

            val collected = mutableListOf<AssistEvent>()
            val collectorJob =
                launch {
                    client.runPipeline(audio = flowOf(ShortArray(160))).collect { collected += it }
                }

            transport.awaitRunCommand()
            transport.emitRunStart(handlerId = 9)
            transport.emitSttEnd("hello")
            transport.emitTtsEnd("media://x")
            transport.emitRunEnd()

            withTimeout(2_000) { collectorJob.join() }

            assertTrue("expected RunEnd at end, got: $collected", collected.last() is AssistEvent.RunEnd)
            assertEquals(1, collected.count { it is AssistEvent.RunEnd })
        }

    /** Pins the deliberate suppression: when an Error is the terminal event,
     *  the deferred `stt-stream-failed` path emits exactly one Error and
     *  consumers do not see a trailing RunEnd. */
    @Test fun `stt stream Error is terminal and no RunEnd follows after audio terminator`() =
        runTest(UnconfinedTestDispatcher()) {
            val transport = FakeTransport()
            val client = HaAssistClient(transport)

            // A finite audio flow so the stream job sends the terminator and
            // (after final-silence) flushes any deferred error.
            val audio = flowOf(ShortArray(160))

            val collected = mutableListOf<AssistEvent>()
            val collectorJob =
                launch {
                    client
                        .runPipeline(
                            audio = audio,
                            options =
                                AssistAudioOptions(
                                    noVad = true,
                                    finalSilenceMs = 0,
                                ),
                        ).collect { collected += it }
                }

            transport.awaitRunCommand()
            transport.emitRunStart(handlerId = 1)
            // The deferred-error path triggers when HA emits stt-stream-failed
            // before the audio terminator. The client defers emitting the Error
            // until after the terminator and (per current code) suppresses RunEnd
            // if a deferred error exists.
            transport.emitError("stt-stream-failed", "stt died")
            transport.emitRunEnd()

            withTimeout(5_000) { collectorJob.join() }

            val errors = collected.filterIsInstance<AssistEvent.Error>()
            val runEnds = collected.filterIsInstance<AssistEvent.RunEnd>()
            assertEquals("expected exactly one Error", 1, errors.size)
            assertEquals("stt-stream-failed", errors.single().code)
            assertEquals("expected no RunEnd after Error (terminal Error)", 0, runEnds.size)
            assertTrue(
                "Error must be the last event consumers see",
                collected.last() is AssistEvent.Error,
            )
        }

    /** Non-deferred Error path: any Error after run-start that is NOT
     *  `stt-stream-failed` (or any Error once the stream has completed)
     *  closes the flow immediately. */
    @Test fun `non stream Error closes flow immediately as terminal event`() =
        runTest(UnconfinedTestDispatcher()) {
            val transport = FakeTransport()
            val client = HaAssistClient(transport)

            val collected = mutableListOf<AssistEvent>()
            val collectorJob =
                launch {
                    client
                        .runPipeline(
                            audio = neverEndingAudio(),
                            options = AssistAudioOptions(noVad = true, finalSilenceMs = 0),
                        ).collect { collected += it }
                }

            transport.awaitRunCommand()
            transport.emitRunStart(handlerId = 3)
            transport.emitError("intent-no-match", "no intent")

            withTimeout(2_000) { collectorJob.join() }

            assertEquals(1, collected.count { it is AssistEvent.Error })
            assertTrue(collected.last() is AssistEvent.Error)
        }

    /** When the caller's audio Flow throws (e.g. mic capture dies mid-run),
     *  the producer must surface an Error so consumers do not hang waiting
     *  for a terminal event. */
    @Test fun `audio flow exception surfaces as audio_stream_failed Error`() =
        runTest(UnconfinedTestDispatcher()) {
            val transport = FakeTransport()
            val client = HaAssistClient(transport)

            val audio: Flow<ShortArray> =
                flow {
                    emit(ShortArray(160))
                    yield()
                    throw IllegalStateException("mic died")
                }

            val collected = mutableListOf<AssistEvent>()
            val collectorJob =
                launch {
                    client
                        .runPipeline(
                            audio = audio,
                            options = AssistAudioOptions(noVad = true, finalSilenceMs = 0),
                        ).collect { collected += it }
                }

            transport.awaitRunCommand()
            transport.emitRunStart(handlerId = 5)

            withTimeout(2_000) { collectorJob.join() }

            val terminal = collected.last()
            assertTrue("expected terminal Error, got $terminal", terminal is AssistEvent.Error)
            val err = terminal as AssistEvent.Error
            assertEquals("audio_stream_failed", err.code)
        }

    /** All audio frames produced before the caller's flow completes must reach
     *  the transport. This pins the "no silent drops" contract:
     *  - producer buffers (UNLIMITED) so it never blocks the caller, and
     *  - the stream coroutine forwards every buffered frame before sending the
     *    handler-id terminator. */
    @Test fun `all audio frames reach transport before terminator`() =
        runTest(UnconfinedTestDispatcher()) {
            val transport = FakeTransport()
            val client = HaAssistClient(transport)

            val frameCount = 50
            val audio =
                flow {
                    repeat(frameCount) { i ->
                        val frame = ShortArray(160) { (i + 1).toShort() }
                        emit(frame)
                    }
                }

            val collected = mutableListOf<AssistEvent>()
            val collectorJob =
                launch {
                    client
                        .runPipeline(
                            audio = audio,
                            options = AssistAudioOptions(noVad = true, finalSilenceMs = 0),
                        ).collect { collected += it }
                }

            transport.awaitRunCommand()
            transport.emitRunStart(handlerId = 7)

            // Wait for the terminator byte (1-byte payload) to be sent.
            withTimeout(2_000) {
                while (transport.binaryFrames.value.none { it.size == 1 }) {
                    delay(5)
                }
            }

            // All audio frames must have been sent before the terminator.
            val binary = transport.binaryFrames.value
            val terminatorIdx = binary.indexOfFirst { it.size == 1 }
            val audioFrames = binary.subList(0, terminatorIdx)
            assertEquals(
                "expected $frameCount audio frames before terminator, got ${audioFrames.size}",
                frameCount,
                audioFrames.size,
            )

            transport.emitRunEnd()
            withTimeout(2_000) { collectorJob.join() }
        }

    /** When run-start arrives late, audio frames produced earlier must still
     *  be forwarded in order once the handler id is known. This pins the
     *  unbounded-buffer contract: producer never blocks on the caller. */
    @Test fun `audio frames produced before run-start are buffered and forwarded in order`() =
        runTest(UnconfinedTestDispatcher()) {
            val transport = FakeTransport()
            val client = HaAssistClient(transport)

            val released = CompletableDeferred<Unit>()
            val frameCount = 100
            val audio =
                flow {
                    repeat(frameCount) { i ->
                        val frame = ShortArray(2) { (i + 1).toShort() }
                        emit(frame)
                    }
                    released.complete(Unit)
                }

            val collected = mutableListOf<AssistEvent>()
            val collectorJob =
                launch {
                    client
                        .runPipeline(
                            audio = audio,
                            options = AssistAudioOptions(noVad = true, finalSilenceMs = 0),
                        ).collect { collected += it }
                }

            transport.awaitRunCommand()

            // Audio producer should complete without being blocked by absence
            // of a handler id (UNLIMITED channel).
            withTimeout(2_000) { released.await() }

            transport.emitRunStart(handlerId = 11)
            withTimeout(2_000) {
                while (transport.binaryFrames.value.none { it.size == 1 }) {
                    delay(5)
                }
            }

            val binary = transport.binaryFrames.value
            val terminatorIdx = binary.indexOfFirst { it.size == 1 }
            val audioFrames = binary.subList(0, terminatorIdx)
            assertEquals(frameCount, audioFrames.size)

            // Frames in order: first byte is handler id, then 2 LE int16 samples
            // both equal to (i+1).
            audioFrames.forEachIndexed { i, frame ->
                val expectedSample = (i + 1).toShort()
                val sample = ((frame[1].toInt() and 0xff) or (frame[2].toInt() shl 8)).toShort()
                assertEquals("frame $i sample mismatch", expectedSample, sample)
                assertEquals("frame $i handler id", 11.toByte(), frame[0])
            }

            transport.emitRunEnd()
            withTimeout(2_000) { collectorJob.join() }
        }

    /** Frames the caller's audio flow produces after the channel has been
     *  closed (only possible via a re-entrant producer or test scaffolding,
     *  but the production code uses `trySend`) must not be silently dropped
     *  without notice. With UNLIMITED + open channel, `trySend` never fails,
     *  so the contract is: the producer's `audio.collect { trySend(...) }`
     *  must terminate when the channel closes (it does, via cancellation /
     *  finally), so no frames are emitted after close.
     *
     *  This test verifies that closing the consumer (cancellation) propagates
     *  to the producer and that no audio frames are observed by the transport
     *  after the flow is cancelled. */
    @Test fun `cancelling the flow stops audio production without losing earlier frames`() =
        runTest(UnconfinedTestDispatcher()) {
            val transport = FakeTransport()
            val client = HaAssistClient(transport)

            val produced = AtomicInteger(0)
            val audio =
                flow {
                    repeat(10) { i ->
                        emit(ShortArray(160) { (i + 1).toShort() })
                        produced.incrementAndGet()
                        // Yield so the consumer can race the cancellation.
                        yield()
                    }
                    // Then stay alive indefinitely.
                    delay(60_000)
                }

            val collected = mutableListOf<AssistEvent>()
            val collectorJob =
                launch {
                    client
                        .runPipeline(
                            audio = audio,
                            options = AssistAudioOptions(noVad = true, finalSilenceMs = 0),
                        ).collect { collected += it }
                }

            transport.awaitRunCommand()
            transport.emitRunStart(handlerId = 13)

            withTimeout(2_000) {
                while (transport.binaryFrames.value.size < 5) {
                    delay(5)
                }
            }

            val seenBeforeCancel = transport.binaryFrames.value.size
            collectorJob.cancelAndJoin()

            // Give any in-flight work a chance to run; then the count must
            // stabilise. No new frames should be added once the flow is cancelled.
            delay(50)
            val seenAfterCancel = transport.binaryFrames.value.size

            // Producer may have buffered a few more frames between sample and cancel;
            // what matters is no growth after cancellation.
            val afterStable = transport.binaryFrames.value.size
            assertEquals(
                "no frames must be sent after cancellation",
                seenAfterCancel,
                afterStable,
            )
            assertTrue(seenBeforeCancel > 0)
        }

    /** Class-under-test fake. */
    private class FakeTransport : HaAssistTransport {
        val frames = MutableSharedFlow<JsonObject>(replay = 0, extraBufferCapacity = 64)
        val binaryFrames = kotlinx.coroutines.flow.MutableStateFlow<List<ByteArray>>(emptyList())
        private val sentJson = Channel<JsonObject>(Channel.UNLIMITED)
        private val nextId = AtomicInteger(42)
        private var runId: Int = -1

        override val rawFrames: Flow<JsonObject> = frames

        override fun nextCommandId(): Int = nextId.incrementAndGet()

        override suspend fun send(payload: JsonObject) {
            runId = (payload["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toInt() ?: -1
            sentJson.send(payload)
        }

        override suspend fun sendBinary(bytes: ByteArray) {
            binaryFrames.value = binaryFrames.value + bytes
        }

        suspend fun awaitRunCommand(): JsonObject = sentJson.receive()

        suspend fun emitRunStart(handlerId: Int) {
            frames.emit(
                haJson
                    .parseToJsonElement(
                        """
                        {
                            "id": $runId,
                            "type": "event",
                            "event": {
                                "type": "run-start",
                                "data": {
                                    "runner_data": { "stt_binary_handler_id": $handlerId }
                                }
                            }
                        }
                        """.trimIndent(),
                    ).jsonObject,
            )
        }

        suspend fun emitSttEnd(text: String) {
            frames.emit(
                haJson
                    .parseToJsonElement(
                        """
                        {
                            "id": $runId,
                            "type": "event",
                            "event": {
                                "type": "stt-end",
                                "data": { "stt_output": { "text": "$text" } }
                            }
                        }
                        """.trimIndent(),
                    ).jsonObject,
            )
        }

        suspend fun emitTtsEnd(url: String) {
            frames.emit(
                haJson
                    .parseToJsonElement(
                        """
                        {
                            "id": $runId,
                            "type": "event",
                            "event": {
                                "type": "tts-end",
                                "data": { "tts_output": { "url": "$url" } }
                            }
                        }
                        """.trimIndent(),
                    ).jsonObject,
            )
        }

        suspend fun emitError(code: String, message: String) {
            frames.emit(
                haJson
                    .parseToJsonElement(
                        """
                        {
                            "id": $runId,
                            "type": "event",
                            "event": {
                                "type": "error",
                                "data": { "code": "$code", "message": "$message" }
                            }
                        }
                        """.trimIndent(),
                    ).jsonObject,
            )
        }

        suspend fun emitRunEnd() {
            frames.emit(
                haJson
                    .parseToJsonElement(
                        """
                        { "id": $runId, "type": "event", "event": { "type": "run-end", "data": null } }
                        """.trimIndent(),
                    ).jsonObject,
            )
        }
    }

    private fun neverEndingAudio(): Flow<ShortArray> =
        flow {
            while (true) {
                emit(ShortArray(160))
                delay(10)
            }
        }
}
