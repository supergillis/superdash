package com.superdash.esphome

import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class EsphomeFrameCodecTest {
    @Test
    fun `message type varint round trip`() =
        runTest {
            for (value in listOf(0, 1, 127, 128, 16384, 2_097_151, Int.MAX_VALUE)) {
                val frame = ByteReadChannel(encodeFrame(messageType = value, payload = byteArrayOf())).readEsphomeFrame()
                assertEquals(value, frame.messageType)
            }
        }

    @Test
    fun `encodeFrame produces preamble + length + type + payload`() {
        val payload = byteArrayOf(0x0A, 0x02, 0x68, 0x69)
        val frame = encodeFrame(messageType = 4, payload = payload)
        // [0x00] [varint(4) = 0x04] [varint(4) = 0x04] [4 payload bytes]
        assertArrayEquals(byteArrayOf(0x00, 0x04, 0x04, 0x0A, 0x02, 0x68, 0x69), frame)
    }

    @Test
    fun `readEsphomeFrame parses what encodeFrame produced`() =
        runTest {
            val payload = byteArrayOf(0x10, 0x20, 0x30)
            val frame = ByteReadChannel(encodeFrame(messageType = 9, payload = payload)).readEsphomeFrame()
            assertEquals(EsphomeFrame(messageType = 9, payload = payload), frame)
        }

    @Test
    fun `readEsphomeFrame throws on non-zero preamble`() =
        runTest {
            try {
                ByteReadChannel(byteArrayOf(0x01, 0x00, 0x00)).readEsphomeFrame()
                error("expected throw")
            } catch (illegal: IllegalStateException) {
                // expected: encrypted (0x01) preamble not supported
            }
        }

    @Test
    fun `readEsphomeFrame rejects oversized length`() =
        runTest {
            // A malicious peer sends a length varint far above the payload cap;
            // the codec must reject it before allocating, not attempt a huge array.
            val bytes =
                Buffer()
                    .apply {
                        write(byteArrayOf(0x00))
                        write(encodeVarint(NOISE_MAX_PAYLOAD + 1))
                    }.readByteArray()
            try {
                ByteReadChannel(bytes).readEsphomeFrame()
                error("expected throw")
            } catch (illegal: IllegalArgumentException) {
                // expected: length exceeds the frame payload cap
            }
        }
}
