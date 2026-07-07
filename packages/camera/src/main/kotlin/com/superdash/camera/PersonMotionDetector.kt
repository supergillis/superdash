package com.superdash.camera

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.superdash.core.log.Log
import kotlinx.coroutines.tasks.await

private val log = Log("PersonMotionDetector")

/** "Person" mode: fires while a human face is visible. Uses ML Kit's bundled
 *  face-detection model as a person-presence proxy — cheap, offline, and far
 *  fewer false positives than frame differencing, at the cost of missing
 *  people facing away from the tablet. Detection runs at most once per
 *  [minIntervalMs]; between runs the last result is reused. Init failures
 *  (missing ML Kit runtime) throw from the first process() call, which
 *  CameraController converts into a fallback to frame-diff mode. */
class PersonMotionDetector(
    private val minIntervalMs: Long = 500L,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : MotionDetector {
    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions
                .Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build(),
        )
    }

    private var lastRunMs = Long.MIN_VALUE
    private var lastResult = false

    override suspend fun process(frame: CameraFrame): Boolean {
        val now = nowMs()
        if (now - lastRunMs < minIntervalMs) {
            return lastResult
        }
        lastRunMs = now
        val image =
            InputImage.fromByteArray(
                frame.nv21,
                frame.width,
                frame.height,
                frame.rotationDegrees,
                InputImage.IMAGE_FORMAT_NV21,
            )
        lastResult =
            runCatching { detector.process(image).await().isNotEmpty() }
                .onFailure { log.w("face detection failed", it) }
                .getOrDefault(lastResult)
        return lastResult
    }

    override fun reset() {
        lastResult = false
        lastRunMs = Long.MIN_VALUE
    }
}
