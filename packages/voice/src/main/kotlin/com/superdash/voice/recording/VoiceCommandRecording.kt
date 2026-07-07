package com.superdash.voice.recording

import com.superdash.voice.pipeline.VoiceRunContext
import com.superdash.voice.pipeline.VoiceRunResult
import com.superdash.voice.pipeline.VoiceRunTerminalState
import com.superdash.voice.pipeline.VoiceSttProvider
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

@JvmInline
value class VoiceCommandRecordingGeneration(
    val value: Long,
)

@Serializable
data class VoiceCommandRecordingMetadata(
    val id: String,
    val createdAtEpochMs: Long,
    val wakeWord: String,
    val primaryProvider: String,
    val secondaryProvider: String,
    val expectedText: String?,
    val transcript: String?,
    val finalState: String,
) {
    companion object {
        fun from(
            context: VoiceRunContext,
            result: VoiceRunResult,
        ): VoiceCommandRecordingMetadata =
            VoiceCommandRecordingMetadata(
                id = context.id.value,
                createdAtEpochMs = context.startedAtEpochMs,
                wakeWord = context.wakeWord,
                primaryProvider = context.providerSelection.primary.providerKey,
                secondaryProvider = context.providerSelection.secondary?.providerKey ?: VoiceSttProvider.None.key,
                expectedText = context.fixture?.expectedText,
                transcript = result.transcript,
                finalState = result.terminalState.recordingName(),
            )
    }
}

data class VoiceCommandRecording(
    val metadata: VoiceCommandRecordingMetadata,
    val frames: List<ShortArray>,
)

data class SavedVoiceCommandRecording(
    val wavFile: File,
    val metadataFile: File,
)

internal fun wavBytes(frames: List<ShortArray>): ByteArray {
    val sampleCount = frames.sumOf { frame -> frame.size }
    val dataBytes = sampleCount * 2
    val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".toByteArray())
    buffer.putInt(36 + dataBytes)
    buffer.put("WAVE".toByteArray())
    buffer.put("fmt ".toByteArray())
    buffer.putInt(16)
    buffer.putShort(1.toShort())
    buffer.putShort(1.toShort())
    buffer.putInt(16_000)
    buffer.putInt(16_000 * 2)
    buffer.putShort(2.toShort())
    buffer.putShort(16.toShort())
    buffer.put("data".toByteArray())
    buffer.putInt(dataBytes)
    frames.forEach { frame ->
        frame.forEach { sample -> buffer.putShort(sample) }
    }
    return buffer.array()
}

private fun VoiceRunTerminalState.recordingName(): String =
    when (this) {
        is VoiceRunTerminalState.Completed -> {
            "Completed"
        }
        is VoiceRunTerminalState.Speaking -> {
            "Speaking"
        }
        is VoiceRunTerminalState.Failed -> {
            "Failed"
        }
        VoiceRunTerminalState.Cancelled -> {
            "Cancelled"
        }
    }
