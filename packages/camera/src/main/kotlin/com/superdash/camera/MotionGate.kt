package com.superdash.camera

/** Debounces per-frame detector output into a stable motion state: a
 *  detection turns the gate on and holds it for the configured clear delay,
 *  refreshed by every new detection. */
class MotionGate(
    private val clearDelayMs: () -> Long,
) {
    private var activeUntilMs = Long.MIN_VALUE

    fun update(
        detected: Boolean,
        nowMs: Long,
    ): Boolean {
        if (detected) {
            activeUntilMs = nowMs + clearDelayMs()
        }
        return nowMs < activeUntilMs
    }
}
