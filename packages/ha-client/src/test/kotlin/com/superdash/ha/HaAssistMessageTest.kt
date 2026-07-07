package com.superdash.ha

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HaAssistMessageTest {
    private val json = haJson

    @Test fun `run-start carries stt_binary_handler_id`() {
        val text =
            """
            {"id":7,"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":42}}}}
            """.trimIndent()
        val frame = json.parseToJsonElement(text).jsonObject
        val parsed = HaAssistClient.parsePipelineFrame(frame, runId = 7)
        assertEquals(AssistEvent.RunStart(handlerId = 42), parsed)
    }

    @Test fun `frame for a different runId is ignored`() {
        val text = """{"id":99,"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":1}}}}"""
        val frame = json.parseToJsonElement(text).jsonObject
        assertEquals(null, HaAssistClient.parsePipelineFrame(frame, runId = 7))
    }

    @Test fun `stt-end yields transcript`() {
        val text = """{"id":7,"type":"event","event":{"type":"stt-end","data":{"stt_output":{"text":"hello world"}}}}"""
        val frame = json.parseToJsonElement(text).jsonObject
        assertEquals(AssistEvent.SttEnd("hello world"), HaAssistClient.parsePipelineFrame(frame, runId = 7))
    }

    @Test fun `stt-start is parsed explicitly`() {
        val text = """{"id":7,"type":"event","event":{"type":"stt-start","data":{"metadata":{"sample_rate":16000}}}}"""
        val frame = json.parseToJsonElement(text).jsonObject
        assertEquals(AssistEvent.SttStart, HaAssistClient.parsePipelineFrame(frame, runId = 7))
    }

    @Test fun `tts-end yields media url`() {
        val text = """{"id":7,"type":"event","event":{"type":"tts-end","data":{"tts_output":{"url":"/api/tts_proxy/abc.mp3"}}}}"""
        val frame = json.parseToJsonElement(text).jsonObject
        assertEquals(AssistEvent.TtsEnd("/api/tts_proxy/abc.mp3"), HaAssistClient.parsePipelineFrame(frame, runId = 7))
    }

    @Test fun `error event yields code+message`() {
        val text = """{"id":7,"type":"event","event":{"type":"error","data":{"code":"pipeline_not_found","message":"no pipeline configured"}}}"""
        val frame = json.parseToJsonElement(text).jsonObject
        val outcome = HaAssistClient.parsePipelineFrame(frame, runId = 7)
        assertTrue(outcome is AssistEvent.Error && outcome.code == "pipeline_not_found")
    }

    @Test fun `successful result ack is dropped`() {
        val text = """{"id":7,"type":"result","success":true,"result":null}"""
        val frame = json.parseToJsonElement(text).jsonObject
        assertEquals(null, HaAssistClient.parsePipelineFrame(frame, runId = 7))
    }

    @Test fun `state_changed frame for unrelated subscription does not throw`() {
        // Regression: rawFrames carries every WS frame including HA's state_changed
        // subscription, which has shape event = { event_type, data, origin, ... }
        // No inner `type` field. Decoding it as AssistPipelineEvent would throw.
        // and kill the whole assist run. Pre-filter on id must skip it cleanly.
        val text =
            """{"id":2,"type":"event","event":{"event_type":"state_changed","data":{"entity_id":"sensor.foo","new_state":null,"old_state":null},"origin":"LOCAL","time_fired":"2026-05-08T12:00:00Z","context":{"id":"abc"}}}"""
        val frame = json.parseToJsonElement(text).jsonObject
        assertEquals(null, HaAssistClient.parsePipelineFrame(frame, runId = 7))
    }

    @Test fun `failed result surfaces as Error`() {
        val text = """{"id":7,"type":"result","success":false,"error":{"code":"pipeline_not_found","message":"no pipeline configured"}}"""
        val frame = json.parseToJsonElement(text).jsonObject
        val outcome = HaAssistClient.parsePipelineFrame(frame, runId = 7)
        assertTrue(outcome is AssistEvent.Error && outcome.code == "pipeline_not_found")
    }

    @Test fun `assist run command serializes to expected wire JSON`() {
        // Wire-format check: data-class refactor must produce the same field
        // names + types + values as the prior buildJsonObject construction.
        val cmd: HaCommand =
            AssistPipelineRunCommand(
                id = 1,
                startStage = "stt",
                endStage = "tts",
                input = AssistPipelineRunInput(sampleRate = 16000),
            )
        val element = haJson.encodeToJsonElement(HaCommand.serializer(), cmd).jsonObject
        assertEquals("assist_pipeline/run", element["type"]?.toString()?.trim('"'))
        assertEquals("1", element["id"]?.toString())
        assertEquals("\"stt\"", element["start_stage"]?.toString())
        assertEquals("\"tts\"", element["end_stage"]?.toString())
        assertEquals("16000", element["input"]?.jsonObject?.get("sample_rate")?.toString())
        // No extra fields beyond the original 5 (type, id, start_stage, end_stage, input).
        assertEquals(setOf("type", "id", "start_stage", "end_stage", "input"), element.keys)
    }

    @Test fun `assist run command serializes no vad when requested`() {
        val cmd: HaCommand =
            AssistPipelineRunCommand(
                id = 1,
                startStage = "stt",
                endStage = "tts",
                input = AssistPipelineRunInput(sampleRate = 16000, noVad = true),
            )

        val element = haJson.encodeToJsonElement(HaCommand.serializer(), cmd).jsonObject

        assertEquals("true", element["input"]?.jsonObject?.get("no_vad")?.toString())
    }

    @Test fun `runPipeline observes run-start emitted during send`() =
        runTest {
            val transport = FastRunStartTransport(json)
            val client = HaAssistClient(transport)

            val event =
                withTimeout(1_000) {
                    client.runPipeline(emptyFlow()).first()
                }

            assertEquals(AssistEvent.RunStart(handlerId = 42), event)
        }

    @Test fun `runPipeline sends buffered audio frames`() =
        runTest {
            val transport = ClosingTransport(json)
            val client = HaAssistClient(transport)
            val frames =
                flow {
                    repeat(8) { index ->
                        emit(shortArrayOf(index.toShort()))
                    }
                }

            client.runPipeline(frames).toList()

            val audioFrames = transport.binary.dropLast(1)
            assertTrue(audioFrames.size > 8)
            assertEquals(1, transport.binary.last().size)
        }

    @Test fun `runPipeline streams after run-start without stt-start`() =
        runTest {
            val transport = ManualRunStartTransport(json)
            val client = HaAssistClient(transport)
            val job =
                launch {
                    client
                        .runPipeline(
                            flow {
                                emit(shortArrayOf(1, 2))
                            },
                        ).toList()
                }

            yield()
            withTimeout(1_000) {
                while (transport.binary.size < 2) {
                    yield()
                }
            }
            assertEquals(2, transport.binary.size)

            job.cancel()
        }

    @Test fun `runPipeline can send terminator without extra final silence`() =
        runTest {
            val transport = ClosingTransport(json)
            val client = HaAssistClient(transport)

            client
                .runPipeline(
                    audio =
                        flow {
                            emit(shortArrayOf(1, 2))
                        },
                    options = AssistAudioOptions(finalSilenceMs = 0),
                ).toList()

            assertEquals(2, transport.binary.size)
            assertEquals(1, transport.binary.last().size)
        }

    @Test fun `runPipeline can stop at intent stage`() =
        runTest {
            val transport = RecordingTransport(json)
            val client = HaAssistClient(transport)

            client
                .runPipeline(
                    audio = emptyFlow(),
                    options = AssistAudioOptions(endStage = AssistPipelineStage.Intent),
                ).take(1)
                .toList()

            val sent = transport.sent.single()
            assertEquals("\"assist_pipeline/run\"", sent["type"].toString())
            assertEquals("\"stt\"", sent["start_stage"].toString())
            assertEquals("\"intent\"", sent["end_stage"].toString())
        }

    private class RecordingTransport(
        private val json: Json,
    ) : HaAssistTransport {
        private val frames = MutableSharedFlow<JsonObject>()
        val sent = mutableListOf<JsonObject>()

        override val rawFrames: Flow<JsonObject> = frames

        override fun nextCommandId(): Int = 7

        override suspend fun send(payload: JsonObject) {
            sent += payload
            frames.emit(
                json
                    .parseToJsonElement(
                        """{"id":7,"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":42}}}}""",
                    ).jsonObject,
            )
        }

        override suspend fun sendBinary(bytes: ByteArray) {}
    }

    private class FastRunStartTransport(
        private val json: Json,
    ) : HaAssistTransport {
        private val frames = MutableSharedFlow<JsonObject>()

        override val rawFrames: Flow<JsonObject> = frames

        override fun nextCommandId(): Int = 7

        override suspend fun send(payload: JsonObject) {
            frames.emit(
                json
                    .parseToJsonElement(
                        """{"id":7,"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":42}}}}""",
                    ).jsonObject,
            )
        }

        override suspend fun sendBinary(bytes: ByteArray) {}
    }

    private class ClosingTransport(
        private val json: Json,
    ) : HaAssistTransport {
        private val frames = MutableSharedFlow<JsonObject>()
        val binary = mutableListOf<ByteArray>()

        override val rawFrames: Flow<JsonObject> = frames

        override fun nextCommandId(): Int = 7

        override suspend fun send(payload: JsonObject) {
            frames.emit(
                json
                    .parseToJsonElement(
                        """{"id":7,"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":42}}}}""",
                    ).jsonObject,
            )
            frames.emit(
                json
                    .parseToJsonElement(
                        """{"id":7,"type":"event","event":{"type":"stt-start","data":{"metadata":{"sample_rate":16000}}}}""",
                    ).jsonObject,
            )
        }

        override suspend fun sendBinary(bytes: ByteArray) {
            binary += bytes
            if (bytes.size == 1) {
                frames.emit(
                    """{"id":7,"type":"event","event":{"type":"run-end","data":null}}""".let {
                        json.parseToJsonElement(it).jsonObject
                    },
                )
            }
        }
    }

    private class ManualRunStartTransport(
        private val json: Json,
    ) : HaAssistTransport {
        private val frames = Channel<JsonObject>(Channel.UNLIMITED)
        val binary = mutableListOf<ByteArray>()

        override val rawFrames: Flow<JsonObject> = frames.receiveAsFlow()

        override fun nextCommandId(): Int = 7

        override suspend fun send(payload: JsonObject) {
            frames.send(
                json
                    .parseToJsonElement(
                        """{"id":7,"type":"event","event":{"type":"run-start","data":{"runner_data":{"stt_binary_handler_id":42}}}}""",
                    ).jsonObject,
            )
        }

        override suspend fun sendBinary(bytes: ByteArray) {
            binary += bytes
            if (bytes.size == 1) {
                frames.send(
                    """{"id":7,"type":"event","event":{"type":"run-end","data":null}}""".let {
                        json.parseToJsonElement(it).jsonObject
                    },
                )
            }
        }
    }
}
