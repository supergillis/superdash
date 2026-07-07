package com.superdash.esphome

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EsphomeCameraChunkerTest {
    @Test
    fun `empty image produces no chunks`() {
        assertEquals(0, cameraImageChunks(key = 1, jpeg = ByteArray(0)).size)
    }

    @Test
    fun `small image is a single done chunk`() {
        val jpeg = ByteArray(100) { it.toByte() }
        val chunks = cameraImageChunks(key = 7, jpeg = jpeg)
        assertEquals(1, chunks.size)
        assertEquals(7, chunks[0].key)
        assertTrue(chunks[0].done)
        assertArrayEquals(jpeg, chunks[0].data.toByteArray())
    }

    @Test
    fun `large image is split with done only on the last chunk`() {
        val jpeg = ByteArray(10) { it.toByte() }
        val chunks = cameraImageChunks(key = 1, jpeg = jpeg, chunkBytes = 4)
        assertEquals(3, chunks.size)
        assertFalse(chunks[0].done)
        assertFalse(chunks[1].done)
        assertTrue(chunks[2].done)
        assertArrayEquals(
            jpeg,
            chunks.flatMap { it.data.toByteArray().toList() }.toByteArray(),
        )
        assertEquals(2, chunks[2].data.size())
    }

    @Test
    fun `exact multiple has full final chunk marked done`() {
        val chunks = cameraImageChunks(key = 1, jpeg = ByteArray(8), chunkBytes = 4)
        assertEquals(2, chunks.size)
        assertTrue(chunks[1].done)
        assertEquals(4, chunks[1].data.size())
    }

    @Test
    fun `default chunk size keeps messages under the noise payload cap`() {
        // 12 bytes of proto overhead headroom: key(fixed32)+tags+len+done.
        assertTrue(CAMERA_CHUNK_BYTES + 32 <= NOISE_MAX_PAYLOAD)
        val chunks = cameraImageChunks(key = Int.MAX_VALUE, jpeg = ByteArray(CAMERA_CHUNK_BYTES + 1))
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].toByteArray().size <= NOISE_MAX_PAYLOAD)
    }
}
