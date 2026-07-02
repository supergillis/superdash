package com.superdash.ha

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HaAssistTextPipelineTest {
    private val json = haJson

    @Test fun `runTextPipeline starts at intent stage with text input`() =
        runTest {
            val transport = RecordingTextTransport(json)
            val client = HaAssistClient(transport)

            val event =
                withTimeout(1_000) {
                    client.runTextPipeline("turn on kitchen").first()
                }

            val sent = transport.sent.single()
            assertEquals(AssistEvent.IntentEnd(JsonObject(emptyMap())), event)
            assertEquals("\"assist_pipeline/run\"", sent["type"].toString())
            assertEquals("\"intent\"", sent["start_stage"].toString())
            assertEquals("\"tts\"", sent["end_stage"].toString())
            assertEquals("\"turn on kitchen\"", sent["input"]?.jsonObject?.get("text").toString())
        }

    @Test fun `runTextPipeline can stop at intent stage with text input`() =
        runTest {
            val transport = RecordingTextTransport(json)
            val client = HaAssistClient(transport)

            val event =
                withTimeout(1_000) {
                    client
                        .runTextPipeline(
                            text = "turn on kitchen",
                            endStage = AssistPipelineStage.Intent,
                        ).first()
                }

            val sent = transport.sent.single()
            assertEquals(AssistEvent.IntentEnd(JsonObject(emptyMap())), event)
            assertEquals("\"assist_pipeline/run\"", sent["type"].toString())
            assertEquals("\"intent\"", sent["start_stage"].toString())
            assertEquals("\"intent\"", sent["end_stage"].toString())
            assertEquals("\"turn on kitchen\"", sent["input"]?.jsonObject?.get("text").toString())
        }

    private class RecordingTextTransport(
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
                        """{"id":7,"type":"event","event":{"type":"intent-end","data":{"intent_output":{}}}}""",
                    ).jsonObject,
            )
        }

        override suspend fun sendBinary(bytes: ByteArray) {}
    }
}
