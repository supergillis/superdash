package com.superdash.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

class WakeWordAudioFixtureTest {
    @Test fun `generated wake word fixtures are labelled and parseable`() {
        val positiveFiles = wavFiles("positive")
        val negativeFiles = wavFiles("negative")

        assertTrue("Expected generated positive wake-word fixtures", positiveFiles.isNotEmpty())
        assertTrue("Expected negative wake-word fixtures", negativeFiles.isNotEmpty())
        assertTrue(
            "Positive fixtures should be labelled for hey_jarvis",
            positiveFiles.all { it.fileName.toString().startsWith("heyjarvis_") },
        )
        assertFalse(
            "Old positive fixtures should not remain",
            positiveFiles.any { !it.fileName.toString().startsWith("heyjarvis_") },
        )

        for (file in positiveFiles + negativeFiles) {
            Files.newInputStream(file).use { input ->
                val samples = readWav16kMonoPcm16(input)
                assertTrue("Fixture ${file.fileName} must contain audio", samples.isNotEmpty())
            }
        }
    }

    private companion object {
        private val fixtureDirectory =
            listOf(
                Path.of("packages/app/src/androidTest/assets/wakeword"),
                Path.of("../app/src/androidTest/assets/wakeword"),
                Path.of("src/androidTest/assets/wakeword"),
            ).first(Files::exists)

        private fun wavFiles(label: String): List<Path> =
            Files
                .list(fixtureDirectory.resolve(label))
                .use { paths ->
                    paths
                        .filter { it.fileName.toString().endsWith(".wav") }
                        .sorted()
                        .collect(Collectors.toList())
                }

        private fun readWav16kMonoPcm16(input: InputStream): ShortArray {
            val bytes = input.readBytes()
            require(bytes.size >= 44) { "WAV too short" }
            require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Not a WAV" }
            var position = 12
            var dataOffset = -1
            var dataSize = 0
            var sampleRate = 0
            var channels = 0
            var bitsPerSample = 0
            while (position + 8 <= bytes.size) {
                val chunkId = String(bytes, position, 4)
                val chunkSize = ByteBuffer.wrap(bytes, position + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                when (chunkId) {
                    "fmt " -> {
                        channels =
                            ByteBuffer
                                .wrap(bytes, position + 10, 2)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .short
                                .toInt()
                        sampleRate = ByteBuffer.wrap(bytes, position + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        bitsPerSample =
                            ByteBuffer
                                .wrap(bytes, position + 22, 2)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .short
                                .toInt()
                    }
                    "data" -> {
                        dataOffset = position + 8
                        dataSize = chunkSize
                    }
                }
                position += 8 + chunkSize
            }
            require(sampleRate == 16000 && channels == 1 && bitsPerSample == 16 && dataOffset >= 0) {
                "WAV must be 16kHz/mono/PCM-16, got rate=$sampleRate ch=$channels bits=$bitsPerSample"
            }
            val sampleCount = dataSize / 2
            val output = ShortArray(sampleCount)
            val buffer = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until sampleCount) {
                output[i] = buffer.short
            }
            return output
        }
    }
}
