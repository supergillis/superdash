package com.superdash.voice.models

import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.io.File

data class VoiceModelDownloadProgress(
    val modelId: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val currentFile: String,
)

open class VoiceModelDownloader(
    private val client: HttpClient,
    filesDir: File,
) {
    private val downloadRoot = File(filesDir, "voice-model-downloads")

    open suspend fun download(
        model: VoiceModelCatalogEntry,
        onProgress: (VoiceModelDownloadProgress) -> Unit,
    ): File {
        val source =
            model.source as? VoiceModelSource.Remote
                ?: error("Model ${model.id} is not downloadable")
        val stagingDir = File(downloadRoot, model.id)
        stagingDir.deleteRecursively()
        stagingDir.mkdirs()
        val totalBytes = model.files.sumOf { file -> file.bytes }
        var downloadedBytes = 0L

        return runCatching {
            for (expectedFile in model.files) {
                val url = "${source.baseUrl.trimEnd('/')}/${expectedFile.remotePath}"
                val target = stagingDir.resolve(expectedFile.relativePath)
                val partial = stagingDir.resolve("${expectedFile.relativePath}.part")
                target.parentFile?.mkdirs()
                partial.parentFile?.mkdirs()
                client.prepareGet(url).execute { response ->
                    check(response.status.isSuccess()) {
                        "Download failed for ${expectedFile.relativePath}: ${response.status}"
                    }
                    response.bodyAsChannel().toInputStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) {
                                    break
                                }
                                output.write(buffer, 0, read)
                                downloadedBytes += read
                                onProgress(
                                    VoiceModelDownloadProgress(
                                        modelId = model.id,
                                        downloadedBytes = downloadedBytes,
                                        totalBytes = totalBytes,
                                        currentFile = expectedFile.relativePath,
                                    ),
                                )
                            }
                        }
                    }
                }
                check(partial.length() == expectedFile.bytes) {
                    "${expectedFile.relativePath} length ${partial.length()} did not match expected ${expectedFile.bytes}"
                }
                check(partial.renameTo(target)) {
                    "Failed to move ${partial.absolutePath} to ${target.absolutePath}"
                }
            }
            stagingDir
        }.getOrElse { throwable ->
            stagingDir.deleteRecursively()
            throw throwable
        }
    }
}
