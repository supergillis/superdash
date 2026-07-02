package com.superdash.ha

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
@SerialName("assist_pipeline/run")
data class AssistPipelineRunCommand(
    val id: Int,
    @SerialName("start_stage") val startStage: String,
    @SerialName("end_stage") val endStage: String,
    val input: AssistPipelineRunInput,
) : HaCommand

enum class AssistPipelineStage(
    val wireValue: String,
) {
    Intent("intent"),
    Tts("tts"),
}

@Serializable
data class AssistPipelineRunInput(
    @SerialName("sample_rate") val sampleRate: Int? = null,
    val text: String? = null,
    @SerialName("no_vad") val noVad: Boolean? = null,
)

/** Inbound assist_pipeline frame envelope, untyped at the discriminator level
 *  because `type` here is `"event"` / `"result"`, not a HaWsFrame variant. */
@Serializable
internal data class AssistPipelineFrame(
    val id: Int? = null,
    val type: String? = null,
    val event: AssistPipelineEvent? = null,
    val success: Boolean = true,
    val error: AssistPipelineErrorBody? = null,
)

@Serializable
internal data class AssistPipelineEvent(
    val type: String,
    val data: JsonElement? = null,
)

@Serializable
internal data class AssistPipelineErrorBody(
    val code: String = "unknown",
    val message: String = "",
)

@Serializable
internal data class AssistRunStartData(
    @SerialName("runner_data") val runnerData: AssistRunnerData? = null,
)

@Serializable
internal data class AssistRunnerData(
    @SerialName("stt_binary_handler_id") val sttBinaryHandlerId: Int,
)

@Serializable
internal data class AssistSttEndData(
    @SerialName("stt_output") val sttOutput: AssistSttOutput? = null,
)

@Serializable
internal data class AssistSttOutput(
    val text: String = "",
)

@Serializable
internal data class AssistTtsEndData(
    @SerialName("tts_output") val ttsOutput: AssistTtsOutput? = null,
)

@Serializable
internal data class AssistTtsOutput(
    val url: String = "",
)

@Serializable
internal data class AssistIntentEndData(
    @SerialName("intent_output") val intentOutput: JsonObject? = null,
)

@Serializable
internal data class AssistErrorEventData(
    val code: String = "unknown",
    val message: String = "",
)
