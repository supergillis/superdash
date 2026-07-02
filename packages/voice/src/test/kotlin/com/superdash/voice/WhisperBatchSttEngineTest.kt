package com.superdash.voice

import com.superdash.voice.stt.RecognitionUpdate
import com.superdash.voice.stt.engines.WhisperBatchSttEngine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhisperBatchSttEngineTest {
    @Test
    fun `recognize buffers audio and emits final transcript`() =
        runTest {
            val native = FakeWhisperNative("turn on the kitchen lights")
            val engine = WhisperBatchSttEngine(nativeFactory = { native })

            val updates = engine.recognize(flowOf(shortArrayOf(1, 2), shortArrayOf(3, 4))).toList()

            assertEquals("turn on the kitchen lights", (updates.single() as RecognitionUpdate.Final).text)
            assertEquals(4, native.receivedSamples)
            assertTrue(native.closed)
        }

    @Test
    fun `recognize emits no final transcript for blank result`() =
        runTest {
            val native = FakeWhisperNative(" ")
            val engine = WhisperBatchSttEngine(nativeFactory = { native })

            val updates = engine.recognize(flowOf(shortArrayOf(1, 2))).toList()

            assertTrue(updates.isEmpty())
            assertTrue(native.closed)
        }

    @Test
    fun `recognize emits no final transcript for empty audio`() =
        runTest {
            val native = FakeWhisperNative("turn on the kitchen lights")
            val engine = WhisperBatchSttEngine(nativeFactory = { native })

            val updates = engine.recognize(flowOf()).toList()

            assertTrue(updates.isEmpty())
            assertEquals(0, native.receivedSamples)
        }

    @Test
    fun `recognize can keep native runtime loaded across requests`() =
        runTest {
            val native = FakeWhisperNative("turn on the kitchen lights")
            var nativeCreates = 0
            val engine =
                WhisperBatchSttEngine(
                    nativeFactory = {
                        nativeCreates += 1
                        native
                    },
                    keepNativeLoaded = true,
                )

            engine.recognize(flowOf(shortArrayOf(1, 2))).toList()
            engine.recognize(flowOf(shortArrayOf(3, 4))).toList()

            assertEquals(1, nativeCreates)
            assertEquals(2, native.receivedSamples)
            assertFalse(native.closed)
        }

    @Test
    fun `recognize serializes access to retained native runtime`() =
        runTest {
            val native = SerialCheckingWhisperNative("turn on the kitchen lights")
            val engine =
                WhisperBatchSttEngine(
                    nativeFactory = { native },
                    keepNativeLoaded = true,
                )

            listOf(
                async { engine.recognize(flowOf(shortArrayOf(1, 2))).toList() },
                async { engine.recognize(flowOf(shortArrayOf(3, 4))).toList() },
            ).awaitAll()

            assertEquals(0, native.concurrentCalls)
            assertFalse(native.closed)
        }

    @Test
    fun `recognize caps default audio passed to native at five seconds`() =
        runTest {
            val fiveSecondsOfSamples = 16000 * 5
            val native = FakeWhisperNative("turn on the kitchen lights")
            val engine = WhisperBatchSttEngine(nativeFactory = { native })

            engine.recognize(flowOf(ShortArray(fiveSecondsOfSamples), shortArrayOf(1))).toList()

            assertEquals(fiveSecondsOfSamples, native.receivedSamples)
        }

    private class FakeWhisperNative(
        private val result: String,
    ) : WhisperNative {
        var receivedSamples = 0
        var closed = false

        override suspend fun transcribe(samples: ShortArray): String {
            receivedSamples = samples.size
            return result
        }

        override fun close() {
            closed = true
        }
    }

    private class SerialCheckingWhisperNative(
        private val result: String,
    ) : WhisperNative {
        var concurrentCalls = 0
        private var active = false
        var closed = false

        override suspend fun transcribe(samples: ShortArray): String {
            if (active) {
                concurrentCalls += 1
            }
            active = true
            delay(10)
            active = false
            return result
        }

        override fun close() {
            closed = true
        }
    }
}
