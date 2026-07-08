package com.superdash.camera

/** One camera frame in NV21 layout: `width*height` luma bytes first, then
 *  interleaved VU chroma. `rotationDegrees` is the rotation needed to make
 *  the image upright (from CameraX's ImageInfo). */
class CameraFrame(
    val nv21: ByteArray,
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val timestampMs: Long,
)
