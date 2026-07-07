package com.superdash.camera

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed class CameraAvailability {
    object Off : CameraAvailability()

    object Running : CameraAvailability()

    object PermissionMissing : CameraAvailability()

    data class Error(
        val message: String,
    ) : CameraAvailability()
}

class CameraPipelineConfig(
    val width: Int,
    val height: Int,
    val facingFront: Boolean,
)

/** Frame source abstraction so [CameraController] is testable without
 *  CameraX. The real implementation is CameraXPipeline (app-side hardware
 *  adapter, Task 4). */
interface CameraPipeline {
    /** NV21 frames while running. Hot; drops frames for slow collectors. */
    val frames: Flow<CameraFrame>

    val availability: StateFlow<CameraAvailability>

    fun start(config: CameraPipelineConfig)

    fun stop()

    /** Upright JPEG for [frame], or null if encoding fails. */
    fun encodeJpeg(
        frame: CameraFrame,
        quality: Int,
    ): ByteArray?
}
