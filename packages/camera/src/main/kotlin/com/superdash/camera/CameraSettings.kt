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

    /** 1..100 */
    val jpegQuality: Flow<Int>

    /** "off" | "motion" | "person" */
    val motionMode: Flow<String>

    /** 0..100, higher = more sensitive */
    val motionSensitivity: Flow<Int>

    val motionClearDelaySec: Flow<Int>

    val wakeOnMotion: Flow<Boolean>

    suspend fun setEnabled(value: Boolean)

    suspend fun setFacing(value: String)

    suspend fun setResolution(value: String)

    suspend fun setJpegQuality(value: Int)

    suspend fun setMotionMode(value: String)

    suspend fun setMotionSensitivity(value: Int)

    suspend fun setMotionClearDelaySec(value: Int)

    suspend fun setWakeOnMotion(value: Boolean)
}
