package com.superdash.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.Looper
import android.util.Range
import android.util.Size
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.superdash.core.log.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors

private val log = Log("CameraXPipeline")

private const val INITIAL_RETRY_DELAY_MS = 5_000L
private const val MAX_RETRY_DELAY_MS = 60_000L

/** Real [CameraPipeline] backed by CameraX ImageAnalysis. All lifecycle and
 *  binding work happens on the main thread; frames are converted to NV21 on
 *  a single analysis executor thread. */
class CameraXPipeline(
    private val context: Context,
) : CameraPipeline {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    private val framesFlow =
        MutableSharedFlow<CameraFrame>(
            extraBufferCapacity = 2,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val frames: Flow<CameraFrame> = framesFlow.asSharedFlow()

    private val availabilityState = MutableStateFlow<CameraAvailability>(CameraAvailability.Off)
    override val availability: StateFlow<CameraAvailability> = availabilityState.asStateFlow()

    private var lifecycleOwner: PipelineLifecycleOwner? = null
    private var provider: ProcessCameraProvider? = null
    private var wantedConfig: CameraPipelineConfig? = null
    private var retryDelayMs = INITIAL_RETRY_DELAY_MS
    private var startGeneration = 0

    override fun start(config: CameraPipelineConfig) {
        mainHandler.post {
            wantedConfig = config
            retryDelayMs = INITIAL_RETRY_DELAY_MS
            startGeneration++
            startOnMain(config)
        }
    }

    override fun stop() {
        mainHandler.post {
            wantedConfig = null
            startGeneration++
            stopOnMain()
        }
    }

    /** Camera-busy / transient failures: retry with exponential backoff while
     *  the pipeline is still wanted. */
    private fun scheduleRetry() {
        val delay = retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        val generation = startGeneration
        mainHandler.postDelayed({
            if (generation != startGeneration) {
                return@postDelayed
            }
            wantedConfig?.let { config ->
                log.i("retrying camera start", "afterMs" to delay)
                startOnMain(config)
            }
        }, delay)
    }

    private fun startOnMain(config: CameraPipelineConfig) {
        stopOnMain()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            log.w("camera permission missing")
            availabilityState.value = CameraAvailability.PermissionMissing
            return
        }
        val owner = PipelineLifecycleOwner()
        lifecycleOwner = owner
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            // start() may have been superseded while the provider resolved.
            if (lifecycleOwner != owner) {
                return@addListener
            }
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                val builder =
                    ImageAnalysis
                        .Builder()
                        .setTargetResolution(Size(config.width, config.height))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                applyAeTargetFpsRange(builder, config)
                val analysis = builder.build()
                val gate = FrameRateGate(config.maxFps)
                analysis.setAnalyzer(analysisExecutor) { imageProxy -> onFrame(imageProxy, gate) }
                val selector =
                    if (config.facingFront) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(owner, selector, analysis)
                owner.moveTo(Lifecycle.State.RESUMED)
                availabilityState.value = CameraAvailability.Running
                // A recovery rebind that reaches Running is a success, so reset the
                // backoff — otherwise the next disconnect (hours later) would wait
                // the accumulated max delay before its first retry.
                retryDelayMs = INITIAL_RETRY_DELAY_MS
                observeCameraState(camera, owner)
                log.i("camera started", "w" to config.width, "h" to config.height, "front" to config.facingFront)
            } catch (throwable: Throwable) {
                log.w("camera start failed", throwable)
                availabilityState.value = CameraAvailability.Error(throwable.message ?: "camera start failed")
                scheduleRetry()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun stopOnMain() {
        provider?.unbindAll()
        provider = null
        lifecycleOwner?.moveTo(Lifecycle.State.DESTROYED)
        lifecycleOwner = null
        availabilityState.value = CameraAvailability.Off
    }

    /** Caps the sensor/ISP rate: picks an AE target FPS range for
     *  [CameraPipelineConfig.maxFps] and applies it via Camera2 interop. When
     *  the characteristics query fails or reports nothing, no option is
     *  applied and only the software gate bounds the rate. */
    @OptIn(ExperimentalCamera2Interop::class)
    private fun applyAeTargetFpsRange(
        builder: ImageAnalysis.Builder,
        config: CameraPipelineConfig,
    ) {
        val available =
            runCatching { availableAeFpsRanges(config.facingFront) }
                .onFailure { log.w("AE fps range query failed", it) }
                .getOrDefault(emptyList())
        val range = selectAeFpsRange(available, config.maxFps) ?: return
        Camera2Interop
            .Extender(builder)
            .setCaptureRequestOption(
                CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,
                Range(range.first, range.second),
            )
        log.i(
            "applying AE target fps range",
            "lower" to range.first,
            "upper" to range.second,
            "maxFps" to config.maxFps,
        )
    }

    private fun availableAeFpsRanges(facingFront: Boolean): List<Pair<Int, Int>> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val wanted =
            if (facingFront) {
                CameraCharacteristics.LENS_FACING_FRONT
            } else {
                CameraCharacteristics.LENS_FACING_BACK
            }
        val cameraId =
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == wanted
            } ?: return emptyList()
        val ranges =
            manager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
        return ranges?.map { range -> range.lower to range.upper }.orEmpty()
    }

    /** Re-open the camera if it closes with an error while still wanted.
     *  A system-forced disconnect (e.g. the device dozing while no camera
     *  foreground service holds access) closes the camera and CameraX does
     *  not reopen it on its own, leaving the stream permanently dead. The
     *  CameraState error signal drives a re-bind through the existing backoff.
     *
     *  Guards, in order:
     *  - `sawOpen`: [CameraInfo.getCameraState] is a LiveData cached per camera
     *    id that replays its last value to a new observer, so a recovery rebind
     *    would otherwise fire synchronously with the PREVIOUS disconnect's
     *    CLOSED(error) and tear the freshly-opened camera down again. Only act
     *    on an error once this bind has actually reached OPEN.
     *  - `scheduled`: at most one retry per bind, so a CLOSING(err)→CLOSED(err)
     *    pair doesn't queue two rebinds.
     *  - `wantedConfig != null`: don't recover after an intentional stop.
     *  - generation guard: ignore a stale observer whose bind was superseded.
     *  The observer is tied to [owner]; stopOnMain() destroys the owner, which
     *  removes it. */
    private fun observeCameraState(
        camera: Camera,
        owner: PipelineLifecycleOwner,
    ) {
        val boundGeneration = startGeneration
        var sawOpen = false
        var scheduled = false
        camera.cameraInfo.cameraState.observe(owner) { state ->
            if (startGeneration != boundGeneration || wantedConfig == null) {
                return@observe
            }
            if (state.type == CameraState.Type.OPEN) {
                sawOpen = true
                return@observe
            }
            if (sawOpen && !scheduled && state.error != null) {
                scheduled = true
                log.w(
                    "camera disconnected; scheduling recovery",
                    null,
                    "code" to state.error?.code,
                    "type" to state.type,
                )
                availabilityState.value =
                    CameraAvailability.Error(state.error?.code?.toString() ?: "camera disconnected")
                scheduleRetry()
            }
        }
    }

    private fun onFrame(
        imageProxy: ImageProxy,
        gate: FrameRateGate,
    ) {
        try {
            if (!gate.admit(System.currentTimeMillis())) {
                return
            }
            val nv21 = imageProxy.toNv21()
            framesFlow.tryEmit(
                CameraFrame(
                    nv21 = nv21,
                    width = imageProxy.width,
                    height = imageProxy.height,
                    rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                    timestampMs = System.currentTimeMillis(),
                ),
            )
        } catch (throwable: Throwable) {
            log.w("frame conversion failed", throwable)
        } finally {
            imageProxy.close()
        }
    }

    override fun encodeJpeg(
        frame: CameraFrame,
        quality: Int,
    ): ByteArray? =
        runCatching {
            val upright = Nv21Rotator.rotate(frame.nv21, frame.width, frame.height, frame.rotationDegrees)
            val yuv = YuvImage(upright.nv21, ImageFormat.NV21, upright.width, upright.height, null)
            val out = ByteArrayOutputStream()
            yuv.compressToJpeg(Rect(0, 0, upright.width, upright.height), quality.coerceIn(1, 100), out)
            out.toByteArray()
        }.onFailure { log.w("jpeg encode failed", it) }.getOrNull()
}

/** Minimal LifecycleOwner so CameraX can bind without an Activity. */
private class PipelineLifecycleOwner : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.CREATED
    }

    fun moveTo(state: Lifecycle.State) {
        registry.currentState = state
    }
}

/** YUV_420_888 → NV21, honoring row and pixel strides. */
private fun ImageProxy.toNv21(): ByteArray {
    val ySize = width * height
    val out = ByteArray(ySize + ySize / 2)
    // Y plane.
    val yPlane = planes[0]
    val yBuffer = yPlane.buffer
    var outPos = 0
    for (row in 0 until height) {
        yBuffer.position(row * yPlane.rowStride)
        if (yPlane.pixelStride == 1) {
            yBuffer.get(out, outPos, width)
            outPos += width
        } else {
            for (col in 0 until width) {
                out[outPos++] = yBuffer.get(row * yPlane.rowStride + col * yPlane.pixelStride)
            }
        }
    }
    // Chroma planes: NV21 wants interleaved VU.
    val uPlane = planes[1]
    val vPlane = planes[2]
    val chromaH = height / 2
    val chromaW = width / 2
    for (row in 0 until chromaH) {
        for (col in 0 until chromaW) {
            out[outPos++] = vPlane.buffer.get(row * vPlane.rowStride + col * vPlane.pixelStride)
            out[outPos++] = uPlane.buffer.get(row * uPlane.rowStride + col * uPlane.pixelStride)
        }
    }
    return out
}
