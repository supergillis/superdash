package com.superdash.voice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.engines.MoonshineBatchSttEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MoonshineBatchSttEngineInstrumentedTest {
    @Test
    fun transcribesCommandFixture() =
        runTest {
            val appContext = ApplicationProvider.getApplicationContext<Context>()
            val testContext = InstrumentationRegistry.getInstrumentation().context
            val samples =
                testContext
                    .assets
                    .open("voice/commands/turn_on_kitchen_lights.wav")
                    .use(::readWav16kMonoPcm16)
            val engine = MoonshineBatchSttEngine.createOrUnavailable(appContext)

            val updates = engine.recognize(flowOf(samples)).toList()
            val text =
                updates
                    .filterIsInstance<RecognitionUpdate.Final>()
                    .lastOrNull()
                    ?.text
                    .orEmpty()

            assertEquals("turn on the kitchen lights", text.lowercase().trimEnd('.'))
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
            val id = String(bytes, position, 4)
            val size = ByteBuffer.wrap(bytes, position + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            when (id) {
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
                    dataSize = size
                }
            }
            position += 8 + size
        }
        require(sampleRate == 16000 && channels == 1 && bitsPerSample == 16 && dataOffset >= 0) {
            "WAV must be 16kHz/mono/PCM-16, got rate=$sampleRate ch=$channels bits=$bitsPerSample"
        }
        val sampleCount = dataSize / 2
        val samples = ShortArray(sampleCount)
        val buffer = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (sampleIdx in 0 until sampleCount) {
            samples[sampleIdx] = buffer.short
        }
        return samples
    }
}
