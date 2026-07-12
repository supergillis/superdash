package com.superdash.camera

/** Caps admitted frames at [maxFps] on average: admissions are scheduled
 *  1000/maxFps ms apart, and a frame is admitted once the schedule comes due.
 *  When arrivals are quantized more coarsely than the schedule (for example a
 *  15 fps sensor under a 10 fps cap), the next slot is measured from the
 *  previous due time rather than the admitted frame, so the average rate
 *  stays at the cap instead of undershooting. After a gap longer than one
 *  interval the schedule resets to the arrival time. Not thread-safe; call
 *  from a single thread. */
internal class FrameRateGate(
    maxFps: Int,
) {
    private val minIntervalMs: Long = 1000L / maxFps.coerceAtLeast(1)
    private var dueMs = Long.MIN_VALUE / 2

    fun admit(nowMs: Long): Boolean {
        if (nowMs < dueMs) {
            return false
        }
        dueMs = if (nowMs - dueMs >= minIntervalMs) nowMs + minIntervalMs else dueMs + minIntervalMs
        return true
    }
}
