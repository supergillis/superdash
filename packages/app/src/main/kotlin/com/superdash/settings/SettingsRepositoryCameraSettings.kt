package com.superdash.settings

import com.superdash.camera.CameraSettings
import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.observe
import com.superdash.core.persistence.write
import kotlinx.coroutines.flow.Flow

/** App-owned [CameraSettings] backed by [KeyValueStore]. */
internal class SettingsRepositoryCameraSettings(
    private val store: KeyValueStore,
) : CameraSettings {
    override val enabled: Flow<Boolean> = store.observe(ENABLED)
    override val facing: Flow<String> = store.observe(FACING)
    override val resolution: Flow<String> = store.observe(RESOLUTION)
    override val jpegQuality: Flow<Int> = store.observe(JPEG_QUALITY)
    override val motionMode: Flow<String> = store.observe(MOTION_MODE)
    override val motionSensitivity: Flow<Int> = store.observe(MOTION_SENSITIVITY)
    override val motionClearDelaySec: Flow<Int> = store.observe(MOTION_CLEAR_DELAY_SEC)
    override val wakeOnMotion: Flow<Boolean> = store.observe(WAKE_ON_MOTION)

    override suspend fun setEnabled(value: Boolean) = store.write(ENABLED, value)

    override suspend fun setFacing(value: String) = store.write(FACING, value)

    override suspend fun setResolution(value: String) = store.write(RESOLUTION, value)

    override suspend fun setJpegQuality(value: Int) = store.write(JPEG_QUALITY, value)

    override suspend fun setMotionMode(value: String) = store.write(MOTION_MODE, value)

    override suspend fun setMotionSensitivity(value: Int) = store.write(MOTION_SENSITIVITY, value)

    override suspend fun setMotionClearDelaySec(value: Int) = store.write(MOTION_CLEAR_DELAY_SEC, value)

    override suspend fun setWakeOnMotion(value: Boolean) = store.write(WAKE_ON_MOTION, value)

    private companion object {
        val ENABLED = Setting(key = "camera_enabled", default = false)
        val FACING = Setting(key = "camera_facing", default = "front")
        val RESOLUTION = Setting(key = "camera_resolution", default = "1280x720")
        val JPEG_QUALITY = Setting(key = "camera_jpeg_quality", default = 60, write = { it.coerceIn(1, 100) })
        val MOTION_MODE = Setting(key = "camera_motion_mode", default = "motion")
        val MOTION_SENSITIVITY =
            Setting(key = "camera_motion_sensitivity", default = 50, write = { it.coerceIn(0, 100) })
        val MOTION_CLEAR_DELAY_SEC =
            Setting(key = "camera_motion_clear_delay_sec", default = 15, write = { it.coerceIn(0, 120) })
        val WAKE_ON_MOTION = Setting(key = "camera_wake_on_motion", default = false)
    }
}
