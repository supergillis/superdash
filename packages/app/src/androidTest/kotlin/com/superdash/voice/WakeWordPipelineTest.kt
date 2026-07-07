package com.superdash.voice

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.superdash.voice.wake.MicroWakeWordRunner
import com.superdash.voice.wake.WakeWordModel
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** End-to-end pipeline test: load WAV fixtures from the test APK's assets,
 *  run them through a fresh [MicroWakeWordRunner] (which uses the real native
 *  AudioFeatureExtractor + the real bundled hey_jarvis.tflite from the target APK),
 *  and assert that positives fire and negatives don't.
 *
 *  Fixtures live at:
 *    app/src/androidTest/assets/wakeword/positive/   (must fire)
 *    app/src/androidTest/assets/wakeword/negative/   (must not fire)
 *  All WAVs must be 16kHz mono PCM-16.
 *
 *  Run: ./gradlew :packages:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.superdash.voice.WakeWordPipelineTest
 */
@RunWith(AndroidJUnit4::class)
class WakeWordPipelineTest {
    private data class Result(
        val name: String,
        val expectedFire: Boolean,
        val frames: Int,
        val peakProb: Float,
        val peakAvg: Float,
        val fires: Int,
    ) {
        val pass: Boolean = (expectedFire && fires > 0) || (!expectedFire && fires == 0)

        fun line() =
            String.format(
                "%-9s %-32s frames=%-4d peak=%.3f peakAvg=%.3f fires=%d %s",
                if (expectedFire) "POSITIVE" else "NEGATIVE",
                name,
                frames,
                peakProb,
                peakAvg,
                fires,
                if (pass) "OK" else "FAIL",
            )
    }

    @Test
    fun fixturesClassifyCorrectly() =
        runBlocking {
            val targetCtx = InstrumentationRegistry.getInstrumentation().targetContext
            val testCtx = InstrumentationRegistry.getInstrumentation().context
            val results = mutableListOf<Result>()

            for (label in listOf("positive", "negative")) {
                val expected = label == "positive"
                val files =
                    testCtx.assets
                        .list("wakeword/$label")
                        .orEmpty()
                        .filter { it.endsWith(".wav") }
                        .sorted()
                for (f in files) {
                    val samples = testCtx.assets.open("wakeword/$label/$f").use(::readWav16kMonoPcm16)
                    results += runOne(targetCtx, "$label/$f", expected, samples)
                }
            }

            println("=".repeat(78))
            println("WakeWordPipelineTest results:")
            for (r in results) println(r.line())
            val failed = results.filter { !it.pass }
            println("Total: ${results.size}, passed: ${results.size - failed.size}, failed: ${failed.size}")
            println("=".repeat(78))
            if (failed.isNotEmpty()) {
                fail("Wake-word fixture mismatches:\n" + failed.joinToString("\n") { it.line() })
            }
        }

    private suspend fun runOne(
        ctx: Context,
        name: String,
        expectedFire: Boolean,
        samples: ShortArray,
    ): Result {
        var peak = 0f
        var peakAvg = 0f
        val window = ArrayDeque<Float>()
        val model = WakeWordModel.require(WakeWordModel.DEFAULT_ID)
        MicroWakeWordRunner(ctx, model.id, probeListener = { p ->
            if (p > peak) {
                peak = p
            }
            window.addLast(p)
            while (window.size > model.slidingWindowAverageSize) {
                window.removeFirst()
            }
            if (window.size == model.slidingWindowAverageSize) {
                val avg = window.average().toFloat()
                if (avg > peakAvg) {
                    peakAvg = avg
                }
            }
        }).use { runner ->
            val frames =
                flow {
                    var i = 0
                    while (i + 160 <= samples.size) {
                        emit(samples.copyOfRange(i, i + 160))
                        i += 160
                    }
                }
            val fires = runner.detect(frames).toList()
            return Result(
                name = name,
                expectedFire = expectedFire,
                frames = samples.size / 160,
                peakProb = peak,
                peakAvg = peakAvg,
                fires = fires.size,
            )
        }
    }

    /** 16kHz mono PCM-16 WAV parser (RIFF/WAVE). Mirrors the production
     *  WakeWordTestReceiver parser; rejects mismatched formats. */
    private fun readWav16kMonoPcm16(input: InputStream): ShortArray {
        val bytes = input.readBytes()
        require(bytes.size >= 44) { "WAV too short" }
        require(String(bytes, 0, 4) == "RIFF" && String(bytes, 8, 4) == "WAVE") { "Not a WAV" }
        var pos = 12
        var dataOffset = -1
        var dataSize = 0
        var sampleRate = 0
        var channels = 0
        var bitsPerSample = 0
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4)
            val size = ByteBuffer.wrap(bytes, pos + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            when (id) {
                "fmt " -> {
                    channels =
                        ByteBuffer
                            .wrap(bytes, pos + 10, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short
                            .toInt()
                    sampleRate = ByteBuffer.wrap(bytes, pos + 12, 4).order(ByteOrder.LITTLE_ENDIAN).int
                    bitsPerSample =
                        ByteBuffer
                            .wrap(bytes, pos + 22, 2)
                            .order(ByteOrder.LITTLE_ENDIAN)
                            .short
                            .toInt()
                }
                "data" -> {
                    dataOffset = pos + 8
                    dataSize = size
                }
            }
            pos += 8 + size
        }
        require(sampleRate == 16000 && channels == 1 && bitsPerSample == 16 && dataOffset >= 0) {
            "WAV must be 16kHz/mono/PCM-16, got rate=$sampleRate ch=$channels bits=$bitsPerSample"
        }
        val sampleCount = dataSize / 2
        val out = ShortArray(sampleCount)
        val bb = ByteBuffer.wrap(bytes, dataOffset, dataSize).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until sampleCount) {
            out[i] = bb.short
        }
        return out
    }
}
