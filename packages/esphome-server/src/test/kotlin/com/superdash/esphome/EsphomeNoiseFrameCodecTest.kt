package com.superdash.esphome

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeByte
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EsphomeNoiseFrameCodecTest {
    @Test
    fun `writeNoiseFrame produces preamble + length + payload`() =
        runTest(UnconfinedTestDispatcher()) {
            val channel = ByteChannel(autoFlush = true)
            channel.writeNoiseFrame(byteArrayOf(0x10, 0x20, 0x30))
            channel.close()
            val all = channel.readEverything()
            assertArrayEquals(byteArrayOf(0x01, 0x00, 0x03, 0x10, 0x20, 0x30), all)
        }

    @Test
    fun `readNoiseFrame consumes one frame`() =
        runTest(UnconfinedTestDispatcher()) {
            val channel = ByteChannel(autoFlush = true)
            val payload = ByteArray(300) { (it and 0xFF).toByte() }
            channel.writeNoiseFrame(payload)
            channel.close()
            val frame = channel.readNoiseFrame(maxPayloadLen = 65_535)
            assertEquals(payload.size, frame.size)
            assertArrayEquals(payload, frame)
        }

    @Test(expected = IllegalStateException::class)
    fun `readNoiseFrame rejects non-noise preamble`() =
        runTest(UnconfinedTestDispatcher()) {
            val channel = ByteChannel(autoFlush = true)
            channel.writeByte(0x00)
            channel.writeByte(0x00)
            channel.writeByte(0x00)
            channel.close()
            channel.readNoiseFrame(maxPayloadLen = 65_535)
        }

    @Test(expected = IllegalStateException::class)
    fun `readNoiseFrame rejects oversized payload`() =
        runTest(UnconfinedTestDispatcher()) {
            val channel = ByteChannel(autoFlush = true)
            channel.writeByte(0x01)
            channel.writeByte(0x10)
            channel.writeByte(0x00)
            channel.close()
            channel.readNoiseFrame(maxPayloadLen = 0x0FFF)
        }

    @Test
    fun `NOISE_PROLOGUE matches ESPHome firmware bytes`() {
        val expected =
            byteArrayOf(
                0x4E,
                0x6F,
                0x69,
                0x73,
                0x65,
                0x41,
                0x50,
                0x49,
                0x49,
                0x6E,
                0x69,
                0x74,
                0x00,
                0x00,
            )
        assertArrayEquals(expected, NOISE_PROLOGUE)
    }
}

private suspend fun io.ktor.utils.io.ByteReadChannel.readEverything(): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(256)
    while (!isClosedForRead) {
        val n = readAvailable(buf, 0, buf.size)
        if (n <= 0) {
            break
        }
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}
