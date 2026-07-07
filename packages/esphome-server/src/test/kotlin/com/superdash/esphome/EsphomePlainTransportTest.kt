package com.superdash.esphome

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EsphomePlainTransportTest {
    @Test
    fun `round trips a frame through PlainTransport`() =
        runTest(UnconfinedTestDispatcher()) {
            val c2s = ByteChannel(autoFlush = true)
            val s2c = ByteChannel(autoFlush = true)
            val client = PlainTransport(input = s2c, output = c2s)
            val server = PlainTransport(input = c2s, output = s2c)

            val payload = byteArrayOf(1, 2, 3, 4)
            client.writeFrame(messageType = 7, payload = payload)
            val frame = server.readFrame()
            assertEquals(7, frame.messageType)
            assertArrayEquals(payload, frame.payload)

            c2s.close()
            s2c.close()
        }
}
