package com.superdash.camera

/** Admits at most [maxFps] frames per second: a frame passes only when at
 *  least 1000/maxFps ms elapsed since the last admitted frame. Not
 *  thread-safe; call from a single thread. */
internal class FrameRateGate(
    maxFps: Int,
) {
    private val minIntervalMs: Long = 1000L / maxFps.coerceAtLeast(1)
    private var lastAdmittedMs = Long.MIN_VALUE / 2

    fun admit(nowMs: Long): Boolean {
        if (nowMs - lastAdmittedMs < minIntervalMs) {
            return false
        }
        lastAdmittedMs = nowMs
        return true
    }
}
