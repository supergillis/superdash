package com.superdash.camera

import kotlin.math.abs

/** Grid cells across / down. 32x24 = 768 cells regardless of input size. */
private const val GRID_W = 32
private const val GRID_H = 24

/** Minimum mean-luma change (0..255) for a grid cell to count as changed.
 *  Filters sensor noise and gradual light drift. */
private const val PIXEL_DELTA = 25

/** Changed-cell fraction required at sensitivity 0 (least sensitive). */
private const val MAX_REQUIRED_FRACTION = 0.20f

/** Changed-cell fraction required at sensitivity 100 (most sensitive). */
private const val MIN_REQUIRED_FRACTION = 0.005f

/** Motion = enough grid cells changed mean luma vs the previous frame.
 *  [sensitivityPercent] is read per frame (0..100, higher = more sensitive). */
class FrameDiffMotionDetector(
    private val sensitivityPercent: () -> Int,
) : MotionDetector {
    private var previous: IntArray? = null

    override suspend fun process(frame: CameraFrame): Boolean {
        val grid = downscaleLuma(frame)
        val prev = previous
        previous = grid
        if (prev == null) {
            return false
        }
        var changed = 0
        for (i in grid.indices) {
            if (abs(grid[i] - prev[i]) > PIXEL_DELTA) {
                changed++
            }
        }
        val sensitivity = sensitivityPercent().coerceIn(0, 100) / 100f
        val required = MAX_REQUIRED_FRACTION + (MIN_REQUIRED_FRACTION - MAX_REQUIRED_FRACTION) * sensitivity
        return changed.toFloat() / grid.size >= required
    }

    override fun reset() {
        previous = null
    }

    override fun close() {}

    /** Mean luma per grid cell, sampled every 4th pixel for speed. */
    private fun downscaleLuma(frame: CameraFrame): IntArray {
        val grid = IntArray(GRID_W * GRID_H)
        val cellW = frame.width / GRID_W
        val cellH = frame.height / GRID_H
        for (gy in 0 until GRID_H) {
            for (gx in 0 until GRID_W) {
                var sum = 0
                var count = 0
                var y = gy * cellH
                while (y < (gy + 1) * cellH) {
                    var x = gx * cellW
                    val rowBase = y * frame.width
                    while (x < (gx + 1) * cellW) {
                        sum += frame.nv21[rowBase + x].toInt() and 0xFF
                        count++
                        x += 4
                    }
                    y += 4
                }
                grid[gy * GRID_W + gx] = if (count == 0) 0 else sum / count
            }
        }
        return grid
    }
}
