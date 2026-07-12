package com.superdash.camera

import kotlinx.coroutines.flow.Flow

/** Typed settings view owned by the camera feature. The interface lives here
 *  so the feature module never imports the persistence layer; `app` provides
 *  the implementation (same pattern as DoorbellSettings). */
interface CameraSettings {
    val enabled: Flow<Boolean>

    /** "front" | "back" */
    val facing: Flow<String>

    /** "640x480" | "1280x720" | "1920x1080" */
    val resolution: Flow<String>

    /** "off" | "motion" | "person" */
    val motionMode: Flow<String>

    /** 0..100, higher = more sensitive */
    val motionSensitivity: Flow<Int>

    val motionClearDelaySec: Flow<Int>

    val wakeOnMotion: Flow<Boolean>

    /** Whether Home Assistant is allowed to remotely turn the camera ON via ESPHome.
     *  Remote OFF is always allowed regardless of this setting. */
    val allowRemoteEnable: Flow<Boolean>

    /** Upper bound on the capture frame rate, 1..30. */
    val maxFps: Flow<Int>

    suspend fun setEnabled(value: Boolean)

    suspend fun setFacing(value: String)

    suspend fun setResolution(value: String)

    suspend fun setMotionMode(value: String)

    suspend fun setMotionSensitivity(value: Int)

    suspend fun setMotionClearDelaySec(value: Int)

    suspend fun setWakeOnMotion(value: Boolean)

    suspend fun setAllowRemoteEnable(value: Boolean)

    suspend fun setMaxFps(value: Int)
}
