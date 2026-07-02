package com.superdash.core.persistence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AtomicFileWriterTest {
    @Test
    fun `write bytes commits target and removes temp file`() {
        val dir = Files.createTempDirectory("atomic-writer").toFile()
        val target = dir.resolve("recording.wav")

        AtomicFileWriter.writeBytes(target, byteArrayOf(1, 2, 3))

        assertEquals(listOf(1, 2, 3), target.readBytes().map { value -> value.toInt() })
        assertEquals(emptyList<String>(), dir.tmpFileNames())
    }

    @Test
    fun `write text commits target and removes temp file`() {
        val dir = Files.createTempDirectory("atomic-writer-text").toFile()
        val target = dir.resolve("recording.json")

        AtomicFileWriter.writeText(target, "{\"ok\":true}")

        assertTrue(target.exists())
        assertEquals("{\"ok\":true}", target.readText())
        assertEquals(emptyList<String>(), dir.tmpFileNames())
    }

    private fun File.tmpFileNames(): List<String> =
        listFiles { file -> file.extension == "tmp" }
            .orEmpty()
            .map { file -> file.name }
}
