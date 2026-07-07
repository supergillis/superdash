package com.superdash.camera

/** Detects motion/presence in a stream of frames. Implementations keep
 *  internal reference state and must be called from a single coroutine. */
interface MotionDetector {
    /** Returns true when this frame contains motion (or a person, depending
     *  on the implementation). */
    suspend fun process(frame: CameraFrame): Boolean

    /** Drops accumulated reference state (e.g. the previous frame). */
    fun reset()
}
