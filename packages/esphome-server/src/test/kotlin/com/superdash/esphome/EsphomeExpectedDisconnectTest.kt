package com.superdash.esphome

import kotlinx.coroutines.channels.ClosedReceiveChannelException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.nio.channels.ClosedChannelException

class EsphomeExpectedDisconnectTest {
    @Test
    fun `broken pipe is an expected disconnect`() {
        assertTrue(isExpectedDisconnect(IOException("Broken pipe")))
    }

    @Test
    fun `connection reset is an expected disconnect`() {
        assertTrue(isExpectedDisconnect(IOException("Connection reset by peer")))
    }

    @Test
    fun `closed channel is an expected disconnect`() {
        assertTrue(isExpectedDisconnect(ClosedChannelException()))
    }

    @Test
    fun `closed receive channel is an expected disconnect`() {
        assertTrue(isExpectedDisconnect(ClosedReceiveChannelException("closed")))
    }

    @Test
    fun `EOF is an expected disconnect`() {
        assertTrue(isExpectedDisconnect(EOFException()))
    }

    @Test
    fun `wrapped broken pipe in cause chain is an expected disconnect`() {
        assertTrue(isExpectedDisconnect(RuntimeException("write failed", IOException("Broken pipe"))))
    }

    @Test
    fun `a protocol bug is not an expected disconnect`() {
        assertFalse(isExpectedDisconnect(IllegalStateException("bad frame")))
        assertFalse(isExpectedDisconnect(IOException("disk full")))
    }
}
