package com.superdash.voice.recording

import com.superdash.core.log.Log
import com.superdash.core.persistence.AtomicFileWriter
import com.superdash.core.persistence.FileMutationRunner
import com.superdash.core.persistence.SerializedFileMutationRunner
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.atomic.AtomicLong

private val log = Log("VoiceCommandRecordingRepository")

class VoiceCommandRecordingRepository(
    private val rootDir: File,
    private val retentionCount: suspend () -> Int,
    private val mutationRunner: FileMutationRunner = SerializedFileMutationRunner(),
) {
    private val json = Json { prettyPrint = false }
    private val clearGeneration = AtomicLong(0L)

    fun currentClearGeneration(): VoiceCommandRecordingGeneration =
        VoiceCommandRecordingGeneration(clearGeneration.get())

    suspend fun save(
        recording: VoiceCommandRecording,
        generation: VoiceCommandRecordingGeneration = currentClearGeneration(),
    ): SavedVoiceCommandRecording {
        val wavFile = File(rootDir, recording.metadata.id + ".wav")
        val metadataFile = File(rootDir, recording.metadata.id + ".json")
        return mutationRunner.mutate {
            rootDir.mkdirs()
            cleanTemporaryFiles()
            try {
                if (generation.value == clearGeneration.get()) {
                    AtomicFileWriter.writeBytes(wavFile, wavBytes(recording.frames))
                    AtomicFileWriter.writeText(metadataFile, json.encodeToString(recording.metadata))
                    cleanOrphans()
                    trimToRetention(retentionCount())
                }
                SavedVoiceCommandRecording(wavFile = wavFile, metadataFile = metadataFile)
            } catch (t: Throwable) {
                deleteExisting(wavFile, "partial recording wav")
                deleteExisting(metadataFile, "partial recording metadata")
                cleanOrphans()
                throw t
            } finally {
                cleanTemporaryFiles()
            }
        }
    }

    suspend fun clear() {
        clearGeneration.incrementAndGet()
        mutationRunner.mutate {
            rootDir.mkdirs()
            rootDir.listFiles().orEmpty().forEach { file -> deleteExisting(file, "recording clear") }
        }
    }

    private fun cleanOrphans() {
        val files = rootDir.listFiles().orEmpty()
        val jsonIds =
            files
                .filter { file -> file.extension == "json" }
                .map { file -> file.nameWithoutExtension }
                .toSet()
        val wavIds =
            files
                .filter { file -> file.extension == "wav" }
                .map { file -> file.nameWithoutExtension }
                .toSet()

        files.forEach { file ->
            when {
                file.extension == "json" && file.nameWithoutExtension !in wavIds -> {
                    deleteExisting(file, "orphan metadata")
                }
                file.extension == "wav" && file.nameWithoutExtension !in jsonIds -> {
                    deleteExisting(file, "orphan wav")
                }
            }
        }
    }

    private fun cleanTemporaryFiles() {
        rootDir
            .listFiles { file -> file.extension == "tmp" }
            .orEmpty()
            .forEach { file -> deleteExisting(file, "temporary recording file") }
    }

    private fun trimToRetention(retentionCount: Int) {
        val recordings =
            rootDir
                .listFiles { file -> file.extension == "json" }
                .orEmpty()
                .mapNotNull { file -> file.toCompleteRecording() }
                .sortedWith(
                    compareBy<CompleteRecording> { recording ->
                        recording.metadata.createdAtEpochMs
                    }.thenBy { recording ->
                        recording.id
                    },
                )
        val extra = recordings.size - retentionCount.coerceAtLeast(0)
        if (extra <= 0) {
            return
        }
        recordings.take(extra).forEach { recording ->
            deleteExisting(recording.wavFile, "retained recording wav")
            deleteExisting(recording.metadataFile, "retained recording metadata")
        }
    }

    private fun File.toCompleteRecording(): CompleteRecording? {
        val metadata =
            runCatching { json.decodeFromString<VoiceCommandRecordingMetadata>(readText()) }
                .getOrElse { throwable ->
                    if (throwable is SerializationException || throwable is IllegalArgumentException) {
                        deleteExisting(this, "unreadable recording metadata")
                        deleteExisting(File(rootDir, nameWithoutExtension + ".wav"), "unreadable recording wav")
                        return null
                    }
                    throw throwable
                }
        val wavFile = File(rootDir, nameWithoutExtension + ".wav")
        if (!wavFile.exists()) {
            return null
        }
        return CompleteRecording(
            id = nameWithoutExtension,
            metadata = metadata,
            metadataFile = this,
            wavFile = wavFile,
        )
    }

    private fun deleteExisting(
        file: File,
        reason: String,
    ) {
        if (!file.exists()) {
            return
        }
        if (!file.delete() && file.exists()) {
            log.w(
                "failed to delete recording file",
                null,
                "reason" to reason,
                "path" to file.absolutePath,
            )
        }
    }

    private data class CompleteRecording(
        val id: String,
        val metadata: VoiceCommandRecordingMetadata,
        val metadataFile: File,
        val wavFile: File,
    )
}
