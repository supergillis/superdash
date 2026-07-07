package com.superdash.core.persistence

import java.io.File
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

object AtomicFileWriter {
    fun writeBytes(
        target: File,
        bytes: ByteArray,
    ) {
        target.parentFile?.mkdirs()
        writeWithTemporaryFile(target) { temporaryFile ->
            temporaryFile.writeBytes(bytes)
        }
    }

    fun writeText(
        target: File,
        text: String,
    ) {
        target.parentFile?.mkdirs()
        writeWithTemporaryFile(target) { temporaryFile ->
            temporaryFile.writeText(text)
        }
    }

    fun writeFromStream(
        target: File,
        input: InputStream,
    ) {
        target.parentFile?.mkdirs()
        writeWithTemporaryFile(target) { temporaryFile ->
            temporaryFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun writeWithTemporaryFile(
        target: File,
        write: (File) -> Unit,
    ) {
        val parent = target.parentFile ?: error("Target must have a parent directory")
        val temporaryFile = Files.createTempFile(parent.toPath(), target.name, ".tmp").toFile()
        try {
            write(temporaryFile)
            moveIntoPlace(temporaryFile, target)
        } catch (t: Throwable) {
            temporaryFile.delete()
            throw t
        }
    }

    private fun moveIntoPlace(
        temporaryFile: File,
        target: File,
    ) {
        try {
            Files.move(
                temporaryFile.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (t: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }
}
