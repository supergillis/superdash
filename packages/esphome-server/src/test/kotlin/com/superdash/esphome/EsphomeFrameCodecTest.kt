package com.superdash.esphome

import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EsphomeFrameCodecTest {
    @Test
    fun `varint round trip`() {
        for (value in listOf(0, 1, 127, 128, 16384, 2_097_151, Int.MAX_VALUE)) {
            val encoded = encodeVarint(value)
            val source = Buffer().apply { write(encoded) }
            assertEquals(value, source.readVarint())
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
    fun `decodeFrame parses what encodeFrame produced`() =
        runTest {
            val payload = byteArrayOf(0x10, 0x20, 0x30)
            val source = Buffer().apply { write(encodeFrame(messageType = 9, payload = payload)) }
            val frame = source.readFrame()
            checkNotNull(frame)
            assertEquals(9, frame.messageType)
            assertArrayEquals(payload, frame.payload)
        }

    @Test
    fun `decodeFrame returns null on EOF before preamble`() =
        runTest {
            val source = Buffer()
            assertNull(source.readFrame())
        }

    @Test
    fun `decodeFrame throws on non-zero preamble`() =
        runTest {
            val source = Buffer().apply { write(byteArrayOf(0x01, 0x00, 0x00)) }
            try {
                source.readFrame()
                error("expected throw")
            } catch (illegal: IllegalStateException) {
                // expected: encrypted (0x01) preamble not supported
            }
        }
}
