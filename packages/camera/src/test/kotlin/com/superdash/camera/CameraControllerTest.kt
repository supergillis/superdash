package com.superdash.camera

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakePipeline : CameraPipeline {
    val framesFlow = MutableSharedFlow<CameraFrame>(extraBufferCapacity = 16)
    override val frames: Flow<CameraFrame> = framesFlow.asSharedFlow()
    val availabilityState = MutableStateFlow<CameraAvailability>(CameraAvailability.Off)
    override val availability: StateFlow<CameraAvailability> = availabilityState.asStateFlow()
    var started: CameraPipelineConfig? = null
    var stopCount = 0
    var startCount = 0

    /** Simulates [CameraXPipeline.stop] posting the unbind asynchronously: when
     *  true, [stop] leaves [availabilityState] at Running instead of flipping
     *  it synchronously, so late frames can still arrive with a lagging
     *  availability signal. */
    var deferAvailabilityOnStop = false

    override fun start(config: CameraPipelineConfig) {
        started = config
        startCount++
        availabilityState.value = CameraAvailability.Running
    }

    override fun stop() {
        stopCount++
        started = null
        if (!deferAvailabilityOnStop) {
            availabilityState.value = CameraAvailability.Off
        }
    }

    override fun encodeJpeg(
        frame: CameraFrame,
        quality: Int,
    ): ByteArray? = byteArrayOf(quality.toByte(), frame.nv21[0])
}

private class FakeSettings : CameraSettings {
    val enabledState = MutableStateFlow(false)
    val facingState = MutableStateFlow("front")
    val resolutionState = MutableStateFlow("1280x720")
    val motionModeState = MutableStateFlow("off")
    val motionSensitivityState = MutableStateFlow(50)
    val motionClearDelaySecState = MutableStateFlow(15)
    val wakeOnMotionState = MutableStateFlow(false)
    val allowRemoteEnableState = MutableStateFlow(true)
    val maxFpsState = MutableStateFlow(10)

    override val enabled: Flow<Boolean> = enabledState
    override val facing: Flow<String> = facingState
    override val resolution: Flow<String> = resolutionState
    override val motionMode: Flow<String> = motionModeState
    override val motionSensitivity: Flow<Int> = motionSensitivityState
    override val motionClearDelaySec: Flow<Int> = motionClearDelaySecState
    override val wakeOnMotion: Flow<Boolean> = wakeOnMotionState
    override val allowRemoteEnable: Flow<Boolean> = allowRemoteEnableState
    override val maxFps: Flow<Int> = maxFpsState

    override suspend fun setEnabled(value: Boolean) = enabledState.emit(value)

    override suspend fun setFacing(value: String) = facingState.emit(value)

    override suspend fun setResolution(value: String) = resolutionState.emit(value)

    override suspend fun setMotionMode(value: String) = motionModeState.emit(value)

    override suspend fun setMotionSensitivity(value: Int) = motionSensitivityState.emit(value)

    override suspend fun setMotionClearDelaySec(value: Int) = motionClearDelaySecState.emit(value)

    override suspend fun setWakeOnMotion(value: Boolean) = wakeOnMotionState.emit(value)

    override suspend fun setAllowRemoteEnable(value: Boolean) = allowRemoteEnableState.emit(value)

    override suspend fun setMaxFps(value: Int) = maxFpsState.emit(value)
}

/** Detector scripted to a fixed answer per frame. */
private class ScriptedDetector(
    private val answer: () -> Boolean,
) : MotionDetector {
    var processed = 0
    var resets = 0
    var closes = 0

    override suspend fun process(frame: CameraFrame): Boolean {
        processed++
        return answer()
    }

    override fun reset() {
        resets++
    }

    override fun close() {
        closes++
    }
}

private fun testFrame(firstByte: Byte = 1): CameraFrame =
    CameraFrame(
        nv21 = ByteArray(6) { firstByte },
        width = 2,
        height = 2,
        rotationDegrees = 0,
        timestampMs = 0L,
    )

@OptIn(ExperimentalCoroutinesApi::class)
class CameraControllerTest {
    @Test
    fun `enabling starts pipeline with parsed resolution and facing`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            val controller =
                CameraController(
                    pipeline = pipeline,
                    settings = settings,
                    detectorFactories = emptyMap(),
                    scope = backgroundScope,
                    nowMs = { 0L },
                )
            assertNull(pipeline.started)

            settings.enabledState.value = true
            assertEquals(1280, pipeline.started?.width)
            assertEquals(720, pipeline.started?.height)
            assertTrue(pipeline.started?.facingFront == true)

            settings.facingState.value = "back"
            assertFalse(pipeline.started?.facingFront == true)

            settings.enabledState.value = false
            assertNull(pipeline.started)
            assertTrue(pipeline.stopCount >= 1)
            // keep controller referenced
            assertEquals(CameraAvailability.Off, controller.availability.value)
        }

    @Test
    fun `motion detection drives motionActive with clear-delay hold`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            var detected = true
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            settings.motionModeState.value = "motion"
            settings.motionClearDelaySecState.value = 10
            val detector = ScriptedDetector { detected }
            val controller =
                CameraController(
                    pipeline = pipeline,
                    settings = settings,
                    detectorFactories = mapOf("motion" to { detector }),
                    scope = backgroundScope,
                    nowMs = { now },
                )
            assertFalse(controller.motionActive.value)

            pipeline.framesFlow.emit(testFrame())
            assertTrue(controller.motionActive.value)

            detected = false
            now = 5_000L
            pipeline.framesFlow.emit(testFrame())
            assertTrue(controller.motionActive.value)

            now = 10_001L
            pipeline.framesFlow.emit(testFrame())
            assertFalse(controller.motionActive.value)
        }

    @Test
    fun `frame arriving after disable does not reactivate motion`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            settings.motionModeState.value = "motion"
            val detector = ScriptedDetector { true }
            val controller =
                CameraController(
                    pipeline = pipeline,
                    settings = settings,
                    detectorFactories = mapOf("motion" to { detector }),
                    scope = backgroundScope,
                    nowMs = { 0L },
                )
            assertFalse(controller.motionActive.value)

            pipeline.framesFlow.emit(testFrame())
            assertTrue(controller.motionActive.value)

            settings.enabledState.value = false
            assertFalse(controller.motionActive.value)
            assertEquals(CameraAvailability.Off, pipeline.availability.value)

            // Simulate a frame that was already in flight when the camera was disabled.
            pipeline.framesFlow.emit(testFrame())
            assertFalse(controller.motionActive.value)
        }

    @Test
    fun `late frame after disable does not repopulate the cached image`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            val controller =
                CameraController(
                    pipeline = pipeline,
                    settings = settings,
                    detectorFactories = emptyMap(),
                    scope = backgroundScope,
                    nowMs = { 0L },
                )
            pipeline.framesFlow.emit(testFrame())
            assertTrue(controller.latestJpeg() != null)

            // Simulate CameraXPipeline.stop() only posting the unbind: availability
            // still reports Running while the disable path has already run.
            pipeline.deferAvailabilityOnStop = true
            settings.enabledState.value = false
            assertNull(controller.latestJpeg())
            assertEquals(CameraAvailability.Running, pipeline.availability.value)

            // A frame that was already in flight arrives after disable.
            pipeline.framesFlow.emit(testFrame())
            assertNull(controller.latestJpeg())
        }

    @Test
    fun `late frame after disable does not reactivate motion even while availability lags`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            settings.motionModeState.value = "motion"
            val detector = ScriptedDetector { true }
            val controller =
                CameraController(
                    pipeline = pipeline,
                    settings = settings,
                    detectorFactories = mapOf("motion" to { detector }),
                    scope = backgroundScope,
                    nowMs = { 0L },
                )
            pipeline.framesFlow.emit(testFrame())
            assertTrue(controller.motionActive.value)

            pipeline.deferAvailabilityOnStop = true
            settings.enabledState.value = false
            assertFalse(controller.motionActive.value)
            assertEquals(CameraAvailability.Running, pipeline.availability.value)

            pipeline.framesFlow.emit(testFrame())
            assertFalse(controller.motionActive.value)
        }

    @Test
    fun `detector resets when the pipeline stops`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            settings.motionModeState.value = "motion"
            val detector = ScriptedDetector { true }
            CameraController(
                pipeline = pipeline,
                settings = settings,
                detectorFactories = mapOf("motion" to { detector }),
                scope = backgroundScope,
                nowMs = { 0L },
            )

            pipeline.framesFlow.emit(testFrame())
            assertTrue(detector.processed > 0)

            settings.enabledState.value = false

            assertTrue(detector.resets > 0)
        }

    @Test
    fun `switching motion mode closes the previous detector`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            settings.motionModeState.value = "motion"
            val motionDetector = ScriptedDetector { true }
            val personDetector = ScriptedDetector { true }
            CameraController(
                pipeline = pipeline,
                settings = settings,
                detectorFactories =
                    mapOf(
                        "motion" to { motionDetector },
                        "person" to { personDetector },
                    ),
                scope = backgroundScope,
                nowMs = { 0L },
            )

            pipeline.framesFlow.emit(testFrame())
            assertEquals(0, motionDetector.closes)

            settings.motionModeState.value = "person"

            assertEquals(1, motionDetector.closes)
            assertEquals(0, personDetector.closes)
        }

    @Test
    fun `mode off processes no frames`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            settings.motionModeState.value = "off"
            val detector = ScriptedDetector { true }
            CameraController(
                pipeline = pipeline,
                settings = settings,
                detectorFactories = mapOf("motion" to { detector }),
                scope = backgroundScope,
                nowMs = { 0L },
            )
            pipeline.framesFlow.emit(testFrame())
            assertEquals(0, detector.processed)
        }

    @Test
    fun `person factory failure falls back to motion mode`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            settings.motionModeState.value = "person"
            val fallbackDetector = ScriptedDetector { true }
            val controller =
                CameraController(
                    pipeline = pipeline,
                    settings = settings,
                    detectorFactories =
                        mapOf(
                            "person" to { error("mlkit unavailable") },
                            "motion" to { fallbackDetector },
                        ),
                    scope = backgroundScope,
                    nowMs = { 0L },
                )
            assertEquals("motion", controller.activeMotionMode.value)
            pipeline.framesFlow.emit(testFrame())
            assertTrue(controller.motionActive.value)
        }

    @Test
    fun `latestJpeg encodes the cached frame with configured quality`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            val controller =
                CameraController(
                    pipeline = pipeline,
                    settings = settings,
                    detectorFactories = emptyMap(),
                    scope = backgroundScope,
                    nowMs = { 0L },
                )
            assertNull(controller.latestJpeg())
            pipeline.framesFlow.emit(testFrame(firstByte = 7))
            assertEquals(listOf<Byte>(60, 7), controller.latestJpeg()?.toList())
        }

    @Test
    fun `jpegFrames emits encoded frames and rate-limits`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            val controller =
                CameraController(
                    pipeline = pipeline,
                    settings = settings,
                    detectorFactories = emptyMap(),
                    scope = backgroundScope,
                    nowMs = { now },
                )
            val received = mutableListOf<ByteArray>()
            backgroundScope.launch { controller.jpegFrames.collect { received.add(it) } }

            pipeline.framesFlow.emit(testFrame())
            now = 50L // < STREAM_MIN_INTERVAL_MS since last emit
            pipeline.framesFlow.emit(testFrame())
            now = 150L
            pipeline.framesFlow.emit(testFrame())
            assertEquals(2, received.size)
        }

    @Test
    fun `requestRestart re-invokes pipeline start while enabled`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = true
            val controller =
                CameraController(
                    pipeline = pipeline,
                    settings = settings,
                    detectorFactories = emptyMap(),
                    scope = backgroundScope,
                    nowMs = { 0L },
                )
            val startsAfterEnable = pipeline.startCount
            assertTrue(startsAfterEnable >= 1)

            controller.requestRestart()

            assertEquals(startsAfterEnable + 1, pipeline.startCount)
        }

    @Test
    fun `requestRestart does nothing while disabled`() =
        runTest(UnconfinedTestDispatcher()) {
            val pipeline = FakePipeline()
            val settings = FakeSettings()
            settings.enabledState.value = false
            CameraController(
                pipeline = pipeline,
                settings = settings,
                detectorFactories = emptyMap(),
                scope = backgroundScope,
                nowMs = { 0L },
            ).requestRestart()

            assertEquals(0, pipeline.startCount)
        }
}
