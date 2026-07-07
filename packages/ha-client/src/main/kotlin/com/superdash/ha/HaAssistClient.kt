package com.superdash.ha

import com.superdash.core.log.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicReference

private val log = Log("HaAssist")
private const val FINAL_SILENCE_FRAME_SAMPLES = 160
private const val FINAL_SILENCE_FRAME_MS = 10L
private const val FINAL_SILENCE_MS = 5_000

data class AssistAudioOptions(
    val sampleRate: Int = 16_000,
    val noVad: Boolean = false,
    val finalSilenceMs: Long = FINAL_SILENCE_MS.toLong(),
    val endStage: AssistPipelineStage = AssistPipelineStage.Tts,
)

sealed interface AssistEvent {
    data class RunStart(
        val handlerId: Int,
    ) : AssistEvent

    data object SttStart : AssistEvent

    data class SttEnd(
        val transcript: String,
    ) : AssistEvent

    data class IntentEnd(
        val response: JsonObject,
    ) : AssistEvent

    data class TtsEnd(
        val mediaUrl: String,
    ) : AssistEvent

    data class Error(
        val code: String,
        val message: String,
    ) : AssistEvent

    data class Other(
        val type: String,
    ) : AssistEvent

    data object RunEnd : AssistEvent
}

/** Drives a single HA assist_pipeline/run cycle over the shared HA WebSocket.
 *
 *  Lifecycle:
 *    1. Allocate runId; send the run command + sample-rate.
 *    2. Wait for run-start event → grab handlerId.
 *    3. Stream caller's audio frames as binary [handlerId, ...pcm16le-LE].
 *    4. When the caller's audio Flow completes (cancelled / VAD ended), send
 *       a single-byte [handlerId] terminator.
 *    5. Forward all assist events (including RunEnd / Error) to the returned
 *       Flow, which closes after RunEnd or Error.
 */
class HaAssistClient internal constructor(
    private val transport: HaAssistTransport,
) {
    constructor(ws: HaWebSocketClient) : this(HaWebSocketTransport(ws))

    fun runTextPipeline(
        text: String,
        endStage: AssistPipelineStage = AssistPipelineStage.Tts,
    ): Flow<AssistEvent> =
        channelFlow {
            val runId = transport.nextCommandId()
            val startedAtNanos = System.nanoTime()
            log.i("runTextPipeline start", "runId" to runId)
            val cmd: HaCommand =
                AssistPipelineRunCommand(
                    id = runId,
                    startStage = AssistPipelineStage.Intent.wireValue,
                    endStage = endStage.wireValue,
                    input = AssistPipelineRunInput(text = text),
                )

            val eventJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    transport.rawFrames
                        .mapNotNull { parsePipelineFrame(it, runId) }
                        .collect { evt ->
                            log.i(
                                "parsed text event",
                                "type" to evt.javaClass.simpleName,
                                "runId" to runId,
                                "elapsedMs" to elapsedMs(startedAtNanos),
                            )
                            when (evt) {
                                is AssistEvent.RunEnd, is AssistEvent.Error -> {
                                    send(evt)
                                    close()
                                }
                                else -> send(evt)
                            }
                        }
                }

            try {
                transport.send(haJson.encodeToJsonElement(cmd) as JsonObject)
                log.i("sent text assist_pipeline/run", "runId" to runId, "elapsedMs" to elapsedMs(startedAtNanos))
            } catch (t: Throwable) {
                log.w("ws.send failed for text assist_pipeline/run", t, "runId" to runId)
                throw t
            }

            awaitClose {
                log.i("runTextPipeline closing", "runId" to runId)
                eventJob.cancel()
            }
        }

    fun runPipeline(
        audio: Flow<ShortArray>,
        options: AssistAudioOptions = AssistAudioOptions(),
    ): Flow<AssistEvent> =
        channelFlow {
            val runId = transport.nextCommandId()
            val startedAtNanos = System.nanoTime()
            log.i("runPipeline start", "runId" to runId)
            val cmd: HaCommand =
                AssistPipelineRunCommand(
                    id = runId,
                    startStage = "stt",
                    endStage = options.endStage.wireValue,
                    input =
                        AssistPipelineRunInput(
                            sampleRate = options.sampleRate,
                            noVad =
                                if (options.noVad) {
                                    true
                                } else {
                                    null
                                },
                        ),
                )

            val handlerIdReady = CompletableDeferred<Int>()
            val streamCompleted = CompletableDeferred<Unit>()
            val deferredStreamError = AtomicReference<AssistEvent.Error?>(null)
            val audioBuffer =
                Channel<ShortArray>(Channel.UNLIMITED)

            val audioCollector =
                launch {
                    try {
                        audio.collect { samples ->
                            audioBuffer.trySend(samples)
                        }
                        audioBuffer.close()
                    } catch (cancellation: kotlinx.coroutines.CancellationException) {
                        audioBuffer.close()
                        throw cancellation
                    } catch (t: Throwable) {
                        // Without this terminal Error, the coordinator stays stuck in Recording
                        // until the 60s assist timeout; the caller's mic flow failing must
                        // surface a typed terminal Error rather than crash the channelFlow.
                        log.w("audio source errored", t, "runId" to runId)
                        audioBuffer.close(t)
                        trySend(AssistEvent.Error("audio_stream_failed", t.message ?: "audio error"))
                        close()
                    }
                }

            // Forward run events; close the flow on terminal events.
            val eventJob =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    transport.rawFrames
                        .mapNotNull { parsePipelineFrame(it, runId) }
                        .collect { evt ->
                            log.i(
                                "parsed event",
                                "type" to evt.logType(),
                                "runId" to runId,
                                "elapsedMs" to elapsedMs(startedAtNanos),
                            )
                            when (evt) {
                                is AssistEvent.RunStart -> {
                                    if (!handlerIdReady.isCompleted) {
                                        handlerIdReady.complete(evt.handlerId)
                                    }
                                    send(evt)
                                }
                                is AssistEvent.SttEnd -> {
                                    deferredStreamError.set(null)
                                    send(evt)
                                }
                                is AssistEvent.Error -> {
                                    if (evt.code == "stt-stream-failed" && !streamCompleted.isCompleted) {
                                        deferredStreamError.set(evt)
                                        log.w(
                                            "deferring STT stream error until audio terminator is sent",
                                            null,
                                            "runId" to runId,
                                            "code" to evt.code,
                                        )
                                    } else {
                                        send(evt)
                                        close()
                                    }
                                }
                                is AssistEvent.RunEnd -> {
                                    if (!streamCompleted.isCompleted && deferredStreamError.get() != null) {
                                        log.i("deferring run-end until audio terminator is sent", "runId" to runId)
                                    } else {
                                        send(evt)
                                        close()
                                    }
                                }
                                else -> send(evt)
                            }
                        }
                }

            try {
                transport.send(haJson.encodeToJsonElement(cmd) as JsonObject)
                log.i("sent assist_pipeline/run", "runId" to runId, "elapsedMs" to elapsedMs(startedAtNanos))
            } catch (t: Throwable) {
                log.w("ws.send failed for assist_pipeline/run", t, "runId" to runId)
                throw t
            }

            // Stream audio once handler id is known. VAD-completion of `audio` triggers
            // the single-byte terminator; HA then drives the rest of the pipeline.
            val streamJob =
                launch {
                    val handlerId = handlerIdReady.await()
                    log.i(
                        "stt_binary_handler_id ready",
                        "handlerId" to handlerId,
                        "runId" to runId,
                        "elapsedMs" to elapsedMs(startedAtNanos),
                    )
                    var frames = 0
                    var samplesTotal = 0L
                    try {
                        for (samples in audioBuffer) {
                            transport.sendBinary(encodeAudioFrame(handlerId, samples))
                            if (frames == 0) {
                                log.i(
                                    "first audio frame sent",
                                    "runId" to runId,
                                    "elapsedMs" to elapsedMs(startedAtNanos),
                                )
                            }
                            frames += 1
                            samplesTotal += samples.size
                        }
                        log.i(
                            "audio flow completed; sending final silence",
                            "runId" to runId,
                            "frames" to frames,
                            "samples" to samplesTotal,
                            "finalSilenceMs" to options.finalSilenceMs,
                            "elapsedMs" to elapsedMs(startedAtNanos),
                        )
                        val silenceFrame = ShortArray(FINAL_SILENCE_FRAME_SAMPLES)
                        val finalSilenceFrames = options.finalSilenceMs / FINAL_SILENCE_FRAME_MS
                        repeat(finalSilenceFrames.toInt()) {
                            transport.sendBinary(encodeAudioFrame(handlerId, silenceFrame))
                            delay(FINAL_SILENCE_FRAME_MS)
                        }
                        log.i(
                            "final silence completed; sending terminator",
                            "runId" to runId,
                            "elapsedMs" to elapsedMs(startedAtNanos),
                        )
                        transport.sendBinary(byteArrayOf(handlerId.toByte()))
                        streamCompleted.complete(Unit)
                        delay(1_000)
                        val error = deferredStreamError.get()
                        if (error != null) {
                            trySend(error)
                            close()
                        }
                    } catch (cancellation: kotlinx.coroutines.CancellationException) {
                        log.i("audio stream cancelled", "runId" to runId, "frames" to frames)
                        throw cancellation
                    } catch (t: Throwable) {
                        // Without this terminal Error, the coordinator stays stuck in Recording
                        // until the 60s assist timeout; channelFlow has no other signal that
                        // audio capture failed.
                        log.w("audio stream errored", t, "runId" to runId, "frames" to frames)
                        trySend(AssistEvent.Error("audio_stream_failed", t.message ?: "audio error"))
                        close()
                    }
                }

            awaitClose {
                log.i("runPipeline closing", "runId" to runId)
                eventJob.cancel()
                audioCollector.cancel()
                streamJob.cancel()
            }
        }

    companion object {
        private fun elapsedMs(startedAtNanos: Long): Long = (System.nanoTime() - startedAtNanos) / 1_000_000L

        /** Pure parser used both by the runtime client and the unit tests. */
        fun parsePipelineFrame(frame: JsonObject, runId: Int): AssistEvent? {
            // Cheap pre-filter: skip frames not for our run before paying for full decode.
            // Without this, state_changed subscription frames (id=2, event_type-shaped
            // payload) would be decoded as AssistPipelineFrame and throw on the nested
            // event { type } that doesn't exist there, killing the whole assist run.
            val frameId = (frame["id"] as? JsonPrimitive)?.intOrNull
            if (frameId != runId) {
                return null
            }
            val envelope =
                runCatching { haJson.decodeFromJsonElement<AssistPipelineFrame>(frame) }
                    .getOrNull() ?: return null
            return when (envelope.type) {
                "result" -> {
                    if (envelope.success) {
                        null
                    } else {
                        val err = envelope.error
                        AssistEvent.Error(err?.code ?: "unknown", err?.message.orEmpty())
                    }
                }
                "event" -> {
                    val event = envelope.event ?: return null
                    parseEventPayload(event)
                }
                else -> null
            }
        }

        private fun parseEventPayload(event: AssistPipelineEvent): AssistEvent =
            when (event.type) {
                "run-start" -> {
                    val hid = event.decode<AssistRunStartData>()?.runnerData?.sttBinaryHandlerId
                    if (hid != null) {
                        AssistEvent.RunStart(hid)
                    } else {
                        AssistEvent.Other("run-start")
                    }
                }
                "stt-start" -> AssistEvent.SttStart
                "stt-end" ->
                    AssistEvent.SttEnd(
                        event
                            .decode<AssistSttEndData>()
                            ?.sttOutput
                            ?.text
                            .orEmpty(),
                    )
                "intent-end" ->
                    AssistEvent.IntentEnd(
                        event.decode<AssistIntentEndData>()?.intentOutput ?: JsonObject(emptyMap()),
                    )
                "tts-end" ->
                    AssistEvent.TtsEnd(
                        event
                            .decode<AssistTtsEndData>()
                            ?.ttsOutput
                            ?.url
                            .orEmpty(),
                    )
                "error" -> {
                    val payload = event.decode<AssistErrorEventData>()
                    log.w("assist error event raw", null, "data" to event.data)
                    AssistEvent.Error(payload?.code ?: "unknown", payload?.message.orEmpty())
                }
                "run-end" -> AssistEvent.RunEnd
                else -> AssistEvent.Other(event.type)
            }

        // HA emits `data: null` for some sub-events (e.g. wake_word-start, run-end);
        // catching here also tolerates schema drift on fields we don't model.
        private inline fun <reified T> AssistPipelineEvent.decode(): T? =
            data?.let { runCatching { haJson.decodeFromJsonElement<T>(it) }.getOrNull() }

        /** Prepend the single-byte handler id to little-endian PCM-16 samples. */
        fun encodeAudioFrame(handlerId: Int, samples: ShortArray): ByteArray {
            val buffer =
                ByteBuffer
                    .allocate(1 + samples.size * 2)
                    .order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(handlerId.toByte())
            for (sample in samples) buffer.putShort(sample)
            return buffer.array()
        }

        private fun AssistEvent.logType(): String =
            when (this) {
                is AssistEvent.Error -> "Error"
                is AssistEvent.IntentEnd -> "IntentEnd"
                is AssistEvent.Other -> "Other($type)"
                is AssistEvent.RunStart -> "RunStart"
                AssistEvent.RunEnd -> "RunEnd"
                AssistEvent.SttStart -> "SttStart"
                is AssistEvent.SttEnd -> "SttEnd"
                is AssistEvent.TtsEnd -> "TtsEnd"
            }
    }
}

internal interface HaAssistTransport {
    val rawFrames: Flow<JsonObject>

    fun nextCommandId(): Int

    suspend fun send(payload: JsonObject)

    suspend fun sendBinary(bytes: ByteArray)
}

private class HaWebSocketTransport(
    private val ws: HaWebSocketClient,
) : HaAssistTransport {
    override val rawFrames: Flow<JsonObject> = ws.rawFrames

    override fun nextCommandId(): Int = ws.nextCommandId()

    override suspend fun send(payload: JsonObject) {
        ws.send(payload)
    }

    override suspend fun sendBinary(bytes: ByteArray) {
        ws.sendBinary(bytes)
    }
}
