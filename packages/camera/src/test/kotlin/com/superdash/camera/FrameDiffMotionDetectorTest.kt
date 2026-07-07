package com.superdash.camera

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDiffMotionDetectorTest {
    private val width = 320
    private val height = 240

    private fun frame(luma: ByteArray): CameraFrame {
        // NV21 = luma plane + interleaved VU; chroma content is irrelevant here.
        val nv21 = luma.copyOf(width * height * 3 / 2)
        return CameraFrame(nv21, width, height, rotationDegrees = 0, timestampMs = 0L)
    }

    private fun flatLuma(value: Byte): ByteArray = ByteArray(width * height) { value }

    private fun lumaWithBrightBlock(
        blockX: Int,
        blockY: Int,
        blockSize: Int = 80,
    ): ByteArray {
        val luma = flatLuma(20)
        for (y in blockY until (blockY + blockSize).coerceAtMost(height)) {
            for (x in blockX until (blockX + blockSize).coerceAtMost(width)) {
                luma[y * width + x] = 200.toByte()
            }
        }
        return luma
    }

    @Test
    fun `first frame is never motion`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 50 })
            assertFalse(detector.process(frame(lumaWithBrightBlock(0, 0))))
        }

    @Test
    fun `identical frames are not motion`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 50 })
            detector.process(frame(flatLuma(64)))
            assertFalse(detector.process(frame(flatLuma(64))))
        }

    @Test
    fun `moving bright block is motion`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 50 })
            detector.process(frame(lumaWithBrightBlock(0, 0)))
            assertTrue(detector.process(frame(lumaWithBrightBlock(160, 100))))
        }

    @Test
    fun `small luma noise below pixel delta is not motion`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 100 })
            detector.process(frame(flatLuma(64)))
            assertFalse(detector.process(frame(flatLuma(70)))) // delta 6 < PIXEL_DELTA
        }

    @Test
    fun `low sensitivity ignores small block that high sensitivity catches`() =
        runTest {
            val insensitive = FrameDiffMotionDetector(sensitivityPercent = { 0 })
            insensitive.process(frame(flatLuma(20)))
            assertFalse(insensitive.process(frame(lumaWithBrightBlock(0, 0, blockSize = 40))))

            val sensitive = FrameDiffMotionDetector(sensitivityPercent = { 100 })
            sensitive.process(frame(flatLuma(20)))
            assertTrue(sensitive.process(frame(lumaWithBrightBlock(0, 0, blockSize = 40))))
        }

    @Test
    fun `reset drops the reference frame`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 100 })
            detector.process(frame(flatLuma(20)))
            detector.reset()
            // Would be motion vs the pre-reset reference, but reset makes it a first frame.
            assertFalse(detector.process(frame(lumaWithBrightBlock(0, 0))))
        }
}
