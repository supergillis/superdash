package com.superdash.camera

import com.superdash.core.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val log = Log("CameraController")

/** Minimum ms between JPEG stream frames (~10 fps cap). */
internal const val STREAM_MIN_INTERVAL_MS = 100L

private const val DEFAULT_RESOLUTION = "1280x720"

private const val JPEG_QUALITY = 60

internal fun parseResolution(value: String): Pair<Int, Int> {
    val parts = value.split("x")
    val width = parts.getOrNull(0)?.toIntOrNull()
    val height = parts.getOrNull(1)?.toIntOrNull()
    if (width == null || height == null || width <= 0 || height <= 0) {
        return parseResolution(DEFAULT_RESOLUTION)
    }
    return width to height
}

/** Orchestrates the camera feature: starts/stops the pipeline from settings,
 *  runs the selected motion detector behind a [MotionGate], caches the latest
 *  frame for single-image requests, and exposes a rate-limited JPEG stream. */
class CameraController(
    private val pipeline: CameraPipeline,
    private val settings: CameraSettings,
    private val detectorFactories: Map<String, () -> MotionDetector>,
    private val scope: CoroutineScope,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val latestFrame = MutableStateFlow<CameraFrame?>(null)
    private val motionActiveState = MutableStateFlow(false)

    /** Synchronous "should the pipeline be running" flag, written only from
     *  [runPipelineControl]. [CameraPipeline.availability] lags disable
     *  because [CameraXPipeline.stop] only posts the unbind to its main
     *  handler; frames already in flight can still arrive afterwards. This
     *  flag lets frame consumers reject those late frames immediately,
     *  instead of trusting the lagging availability signal. */
    @Volatile
    private var pipelineWanted = false
    private val activeMotionModeState = MutableStateFlow("off")
    private val jpegFramesFlow =
        MutableSharedFlow<ByteArray>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val motionActive: StateFlow<Boolean> = motionActiveState.asStateFlow()

    /** The mode actually running; differs from the setting after fallback. */
    val activeMotionMode: StateFlow<String> = activeMotionModeState.asStateFlow()

    val availability: StateFlow<CameraAvailability> get() = pipeline.availability

    /** Encoded JPEG frames, capped at [STREAM_MIN_INTERVAL_MS]. Hot while the
     *  pipeline runs; encoding is skipped when nobody collects. */
    val jpegFrames: Flow<ByteArray> = jpegFramesFlow.asSharedFlow()

    private val restartRequests =
        MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val clearDelaySec: StateFlow<Int> =
        settings.motionClearDelaySec.stateIn(scope, SharingStarted.Eagerly, 15)

    init {
        scope.launch { runPipelineControl() }
        scope.launch { cacheAndEncodeFrames() }
        scope.launch { runMotionDetection() }
    }

    suspend fun latestJpeg(): ByteArray? =
        latestFrame.value?.let { frame -> pipeline.encodeJpeg(frame, JPEG_QUALITY) }

    /** Forces the pipeline to re-run its start with the current settings, even
     *  though no setting changed. Used to recover capture after the CAMERA
     *  permission is granted while the camera is already enabled — a same-value
     *  settings write is filtered by distinctUntilChanged, so nothing else
     *  re-attempts the start. No-op while the camera is disabled. */
    fun requestRestart() {
        restartRequests.tryEmit(Unit)
    }

    private suspend fun runPipelineControl() {
        val config =
            combine(
                settings.enabled,
                settings.resolution,
                settings.facing,
                settings.maxFps,
            ) { enabled, resolution, facing, maxFps ->
                PipelineSettings(enabled, resolution, facing, maxFps)
            }.distinctUntilChanged()
        combine(config, restartRequests.onStart { emit(Unit) }) { current, _ -> current }
            .collect { wanted ->
                if (wanted.enabled) {
                    val (width, height) = parseResolution(wanted.resolution)
                    pipelineWanted = true
                    pipeline.start(
                        CameraPipelineConfig(
                            width = width,
                            height = height,
                            facingFront = wanted.facing != "back",
                            maxFps = wanted.maxFps,
                        ),
                    )
                } else {
                    pipelineWanted = false
                    pipeline.stop()
                    latestFrame.value = null
                    motionActiveState.value = false
                }
            }
    }

    private suspend fun cacheAndEncodeFrames() {
        var lastStreamEmitMs = -STREAM_MIN_INTERVAL_MS
        pipeline.frames.collect { frame ->
            if (!pipelineWanted) return@collect
            latestFrame.value = frame
            val now = nowMs()
            if (jpegFramesFlow.subscriptionCount.value > 0 &&
                now - lastStreamEmitMs >= STREAM_MIN_INTERVAL_MS
            ) {
                pipeline.encodeJpeg(frame, JPEG_QUALITY)?.let { jpeg ->
                    lastStreamEmitMs = now
                    jpegFramesFlow.emit(jpeg)
                }
            }
        }
    }

    private suspend fun runMotionDetection() {
        settings.motionMode.distinctUntilChanged().collectLatest { mode ->
            motionActiveState.value = false
            val detector = createDetector(mode) ?: return@collectLatest
            val gate = MotionGate(clearDelayMs = { clearDelaySec.value * 1000L })
            try {
                coroutineScope {
                    launch {
                        pipeline.availability.collect { state ->
                            if (state !is CameraAvailability.Running) {
                                detector.reset()
                                // Belt-and-braces: availability can lag the actual
                                // disable (see pipelineWanted), but when it does
                                // report non-Running, make sure the latch clears.
                                motionActiveState.value = false
                            }
                        }
                    }
                    pipeline.frames.collect { frame ->
                        val detected =
                            runCatching { detector.process(frame) }
                                .onFailure { log.w("motion detector failed", it) }
                                .getOrDefault(false)
                        val active = gate.update(detected, nowMs())
                        if (pipelineWanted) {
                            motionActiveState.value = active
                        }
                    }
                }
            } finally {
                detector.close()
            }
        }
    }

    /** Builds the detector for [mode]; on failure falls back to "motion". */
    private fun createDetector(mode: String): MotionDetector? {
        if (mode == "off") {
            activeMotionModeState.value = "off"
            return null
        }
        val primary = detectorFactories[mode]
        val fromPrimary =
            primary?.let { factory ->
                runCatching { factory() }
                    .onFailure { log.w("detector init failed; falling back", it, "mode" to mode) }
                    .getOrNull()
            }
        if (fromPrimary != null) {
            activeMotionModeState.value = mode
            return fromPrimary
        }
        val fallback = detectorFactories["motion"]?.let { runCatching { it() }.getOrNull() }
        activeMotionModeState.value = if (fallback != null) "motion" else "off"
        return fallback
    }
}

private data class PipelineSettings(
    val enabled: Boolean,
    val resolution: String,
    val facing: String,
    val maxFps: Int,
)
