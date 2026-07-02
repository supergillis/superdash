package com.superdash.voice.models

import com.superdash.core.persistence.AtomicFileWriter
import com.superdash.core.persistence.FileMutationRunner
import com.superdash.core.persistence.SerializedFileMutationRunner
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.time.Instant

@Serializable
data class InstalledVoiceModelMetadata(
    val modelId: String,
    val installedAtEpochMs: Long,
    val bytes: Long,
    val files: List<InstalledVoiceModelFile>,
)

@Serializable
data class InstalledVoiceModelFile(
    val relativePath: String,
    val bytes: Long,
    val sha256: String,
)

class VoiceModelStore(
    filesDir: File,
    private val mutationRunner: FileMutationRunner = SerializedFileMutationRunner(),
) {
    private val json = Json { prettyPrint = true }
    private val rootDir = File(filesDir, "voice-models")
    private val metadataFile = File(rootDir, "installed-models.json")

    fun modelDir(modelId: String): File = File(rootDir, modelId)

    suspend fun installedModels(): List<InstalledVoiceModelMetadata> =
        mutationRunner.mutate {
            readInstalledModels()
        }

    suspend fun installFromAssets(
        model: VoiceModelCatalogEntry,
        assetOpener: (String) -> InputStream,
    ): InstalledVoiceModelMetadata {
        val source =
            model.source as? VoiceModelSource.BundledAsset
                ?: error("Model ${model.id} is not backed by bundled assets")
        return install(model) { expectedFile ->
            assetOpener("${source.assetDir}/${expectedFile.relativePath}")
        }
    }

    suspend fun installFromDirectory(
        model: VoiceModelCatalogEntry,
        stagingDir: File,
    ): InstalledVoiceModelMetadata =
        install(model) { expectedFile ->
            stagingDir.resolve(expectedFile.relativePath).inputStream()
        }

    suspend fun delete(modelId: String) {
        mutationRunner.mutate {
            modelDir(modelId).deleteRecursively()
            writeMetadata(readInstalledModels().filterNot { metadata -> metadata.modelId == modelId })
        }
    }

    private fun readInstalledModels(): List<InstalledVoiceModelMetadata> {
        if (!metadataFile.exists()) {
            return emptyList()
        }
        return json.decodeFromString(ListSerializer(InstalledVoiceModelMetadata.serializer()), metadataFile.readText())
    }

    private suspend fun install(
        model: VoiceModelCatalogEntry,
        openInput: (VoiceModelFile) -> InputStream,
    ): InstalledVoiceModelMetadata =
        mutationRunner.mutate {
            installLocked(model = model, openInput = openInput)
        }

    private fun installLocked(
        model: VoiceModelCatalogEntry,
        openInput: (VoiceModelFile) -> InputStream,
    ): InstalledVoiceModelMetadata {
        val targetDir = modelDir(model.id)
        val temporaryDir = File(rootDir, "${model.id}.installing")
        val backupDir = File(rootDir, "${model.id}.replacing")
        temporaryDir.deleteRecursively()
        backupDir.deleteRecursively()
        temporaryDir.mkdirs()

        val previousMetadata = readInstalledModels()
        var targetReplaced = false
        return runCatching {
            val installedFiles =
                model.files.map { expectedFile ->
                    val target = temporaryDir.resolve(expectedFile.relativePath)
                    target.parentFile?.mkdirs()
                    openInput(expectedFile).use { input ->
                        target.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    verifyFile(target, expectedFile)
                    InstalledVoiceModelFile(
                        relativePath = expectedFile.relativePath,
                        bytes = target.length(),
                        sha256 = target.sha256(),
                    )
                }
            if (targetDir.exists()) {
                check(targetDir.renameTo(backupDir)) {
                    "Failed to move ${targetDir.absolutePath} to ${backupDir.absolutePath}"
                }
            }
            check(temporaryDir.renameTo(targetDir)) {
                if (backupDir.exists()) {
                    backupDir.renameTo(targetDir)
                }
                "Failed to move ${temporaryDir.absolutePath} to ${targetDir.absolutePath}"
            }
            targetReplaced = true
            val metadata =
                InstalledVoiceModelMetadata(
                    modelId = model.id,
                    installedAtEpochMs = Instant.now().toEpochMilli(),
                    bytes = installedFiles.sumOf { file -> file.bytes },
                    files = installedFiles,
                )
            writeMetadata(readInstalledModels().filterNot { metadata -> metadata.modelId == model.id } + metadata)
            backupDir.deleteRecursively()
            metadata
        }.getOrElse { throwable ->
            temporaryDir.deleteRecursively()
            if (targetReplaced) {
                targetDir.deleteRecursively()
                if (backupDir.exists()) {
                    backupDir.renameTo(targetDir)
                    runCatching { writeMetadata(previousMetadata) }
                }
            } else {
                backupDir.deleteRecursively()
            }
            throw throwable
        }
    }

    private fun verifyFile(
        file: File,
        expectedFile: VoiceModelFile,
    ) {
        check(file.length() == expectedFile.bytes) {
            "${expectedFile.relativePath} length ${file.length()} did not match expected ${expectedFile.bytes}"
        }
        check(file.sha256() == expectedFile.sha256) {
            "${expectedFile.relativePath} checksum did not match expected ${expectedFile.sha256}"
        }
    }

    private fun writeMetadata(metadata: List<InstalledVoiceModelMetadata>) {
        AtomicFileWriter.writeText(
            metadataFile,
            json.encodeToString(ListSerializer(InstalledVoiceModelMetadata.serializer()), metadata),
        )
    }
}

fun File.sha256(): String =
    inputStream().use { input ->
        sha256(input)
    }

fun sha256(value: String): String =
    sha256(value.byteInputStream())

private fun sha256(input: InputStream): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    while (true) {
        val read = input.read(buffer)
        if (read < 0) {
            break
        }
        digest.update(buffer, 0, read)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
