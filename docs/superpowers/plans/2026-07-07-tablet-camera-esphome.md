# Tablet Camera over ESPHome Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expose the tablet's camera to Home Assistant as an ESPHome camera entity with a motion binary sensor, selectable motion/person detection, and local wake-on-motion.

**Architecture:** New `packages/camera` feature module owns the CameraX capture pipeline and motion detection, exposing only flows/suspend functions. `packages/esphome-server` gains a `Camera` entity type plus the three camera protocol messages (43/44/45). `packages/app` wires everything through a new `EsphomeCameraBindings`, a camera foreground service, and a Settings section.

**Tech Stack:** Kotlin, CameraX (`camera-camera2`, `camera-lifecycle`), ML Kit face detection (person mode), protobuf-lite (already generated from `api.proto` — camera messages `CameraImageRequest`, `CameraImageResponse`, `ListEntitiesCameraResponse` already exist in `org.esphome.api`), kotlinx-coroutines, JUnit4 + `kotlinx-coroutines-test`.

**Spec:** `docs/superpowers/specs/2026-07-07-tablet-camera-esphome-design.md`

## Global Constraints

- Android 15+ (API 35) only; arm64 only. JDK 17.
- Every commit must pass `./gradlew ktlintCheck` — run `./gradlew ktlintFormat` before each commit.
- kotlinx-coroutines version is 1.9.0 (inline versions in `gradle/libs.versions.toml`).
- Protobuf classes are generated from `src/main/proto-pristine/api.proto` with the lite runtime into package `org.esphome.api`. Do NOT edit the pristine proto.
- A single native-API message must fit `NOISE_MAX_PAYLOAD = 16 * 1024` bytes (`EsphomeNoiseFrameCodec.kt:26`) — camera images MUST be chunked (≤ 15 KiB data per message).
- Feature modules never import the persistence layer: settings interfaces live in the feature package, implementations in `packages/app` (see `DoorbellSettings` / `SettingsRepositoryDoorbellSettings`).
- Log via `com.superdash.core.log.Log` (`private val log = Log("Tag")`, then `log.i("msg", "k" to v)` / `log.w("msg", throwable, "k" to v)`).
- Entity keys derive from `keyFromObjectId(objectId)`; never send `unique_id`.
- Motion mode strings everywhere: `"off"`, `"motion"`, `"person"`. Camera facing strings: `"front"`, `"back"`. Resolution strings: `"640x480"`, `"1280x720"`, `"1920x1080"`.

---

### Task 1: `packages/camera` module + `FrameDiffMotionDetector`

**Files:**
- Modify: `settings.gradle.kts` (add include)
- Modify: `gradle/libs.versions.toml` (add CameraX + ML Kit entries — inert until used)
- Create: `packages/camera/build.gradle.kts`
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/CameraFrame.kt`
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/MotionDetector.kt`
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/FrameDiffMotionDetector.kt`
- Test: `packages/camera/src/test/kotlin/com/superdash/camera/FrameDiffMotionDetectorTest.kt`

**Interfaces:**
- Consumes: nothing (new module; depends on `:packages:core` only).
- Produces: `CameraFrame(nv21, width, height, rotationDegrees, timestampMs)`, `interface MotionDetector { suspend fun process(frame: CameraFrame): Boolean; fun reset() }`, `class FrameDiffMotionDetector(sensitivityPercent: () -> Int) : MotionDetector`.

- [ ] **Step 1: Module scaffold**

Add to `settings.gradle.kts` after the doorbell include:

```kotlin
include(":packages:camera")
```

Add to `gradle/libs.versions.toml` — in `[versions]`:

```toml
camerax = "1.4.2"
mlkitFaceDetection = "16.1.7"
```

In `[libraries]` (near the other androidx entries):

```toml
androidx-camera-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
androidx-camera-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
mlkit-face-detection = { group = "com.google.mlkit", name = "face-detection", version.ref = "mlkitFaceDetection" }
kotlinx-coroutines-play-services = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-play-services", version = "1.9.0" }
```

Create `packages/camera/build.gradle.kts`:

```kotlin
plugins {
    id("superdash.android.library")
}

android {
    namespace = "com.superdash.camera"
}

dependencies {
    implementation(project(":packages:core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.jdk8)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

(CameraX/ML Kit deps are added in Tasks 4 and 5 when first used.)

Create `packages/camera/src/main/kotlin/com/superdash/camera/CameraFrame.kt`:

```kotlin
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
```

Create `packages/camera/src/main/kotlin/com/superdash/camera/MotionDetector.kt`:

```kotlin
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
```

- [ ] **Step 2: Write the failing tests**

Create `packages/camera/src/test/kotlin/com/superdash/camera/FrameDiffMotionDetectorTest.kt`:

```kotlin
package com.superdash.camera

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameDiffMotionDetectorTest {
    private val width = 320
    private val height = 240

    private fun frame(luma: ByteArray): CameraFrame {
        // NV21 = luma plane + interleaved VU; chroma content is irrelevant here.
        val nv21 = luma.copyOf(width * height * 3 / 2)
        return CameraFrame(nv21, width, height, rotationDegrees = 0, timestampMs = 0L)
    }

    private fun flatLuma(value: Byte): ByteArray = ByteArray(width * height) { value }

    private fun lumaWithBrightBlock(
        blockX: Int,
        blockY: Int,
        blockSize: Int = 80,
    ): ByteArray {
        val luma = flatLuma(20)
        for (y in blockY until (blockY + blockSize).coerceAtMost(height)) {
            for (x in blockX until (blockX + blockSize).coerceAtMost(width)) {
                luma[y * width + x] = 200.toByte()
            }
        }
        return luma
    }

    @Test
    fun `first frame is never motion`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 50 })
            assertFalse(detector.process(frame(lumaWithBrightBlock(0, 0))))
        }

    @Test
    fun `identical frames are not motion`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 50 })
            detector.process(frame(flatLuma(64)))
            assertFalse(detector.process(frame(flatLuma(64))))
        }

    @Test
    fun `moving bright block is motion`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 50 })
            detector.process(frame(lumaWithBrightBlock(0, 0)))
            assertTrue(detector.process(frame(lumaWithBrightBlock(160, 100))))
        }

    @Test
    fun `small luma noise below pixel delta is not motion`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 100 })
            detector.process(frame(flatLuma(64)))
            assertFalse(detector.process(frame(flatLuma(70)))) // delta 6 < PIXEL_DELTA
        }

    @Test
    fun `low sensitivity ignores small block that high sensitivity catches`() =
        runTest {
            val insensitive = FrameDiffMotionDetector(sensitivityPercent = { 0 })
            insensitive.process(frame(flatLuma(20)))
            assertFalse(insensitive.process(frame(lumaWithBrightBlock(0, 0, blockSize = 40))))

            val sensitive = FrameDiffMotionDetector(sensitivityPercent = { 100 })
            sensitive.process(frame(flatLuma(20)))
            assertTrue(sensitive.process(frame(lumaWithBrightBlock(0, 0, blockSize = 40))))
        }

    @Test
    fun `reset drops the reference frame`() =
        runTest {
            val detector = FrameDiffMotionDetector(sensitivityPercent = { 100 })
            detector.process(frame(flatLuma(20)))
            detector.reset()
            // Would be motion vs the pre-reset reference, but reset makes it a first frame.
            assertFalse(detector.process(frame(lumaWithBrightBlock(0, 0))))
        }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :packages:camera:testDebugUnitTest --tests "com.superdash.camera.FrameDiffMotionDetectorTest"`
Expected: FAIL — `FrameDiffMotionDetector` unresolved.

- [ ] **Step 4: Implement**

Create `packages/camera/src/main/kotlin/com/superdash/camera/FrameDiffMotionDetector.kt`:

```kotlin
package com.superdash.camera

import kotlin.math.abs

/** Grid cells across / down. 32x24 = 768 cells regardless of input size. */
private const val GRID_W = 32
private const val GRID_H = 24

/** Minimum mean-luma change (0..255) for a grid cell to count as changed.
 *  Filters sensor noise and gradual light drift. */
private const val PIXEL_DELTA = 25

/** Changed-cell fraction required at sensitivity 0 (least sensitive). */
private const val MAX_REQUIRED_FRACTION = 0.20f

/** Changed-cell fraction required at sensitivity 100 (most sensitive). */
private const val MIN_REQUIRED_FRACTION = 0.005f

/** Motion = enough grid cells changed mean luma vs the previous frame.
 *  [sensitivityPercent] is read per frame (0..100, higher = more sensitive). */
class FrameDiffMotionDetector(
    private val sensitivityPercent: () -> Int,
) : MotionDetector {
    private var previous: IntArray? = null

    override suspend fun process(frame: CameraFrame): Boolean {
        val grid = downscaleLuma(frame)
        val prev = previous
        previous = grid
        if (prev == null) {
            return false
        }
        var changed = 0
        for (i in grid.indices) {
            if (abs(grid[i] - prev[i]) > PIXEL_DELTA) {
                changed++
            }
        }
        val sensitivity = sensitivityPercent().coerceIn(0, 100) / 100f
        val required = MAX_REQUIRED_FRACTION + (MIN_REQUIRED_FRACTION - MAX_REQUIRED_FRACTION) * sensitivity
        return changed.toFloat() / grid.size >= required
    }

    override fun reset() {
        previous = null
    }

    /** Mean luma per grid cell, sampled every 4th pixel for speed. */
    private fun downscaleLuma(frame: CameraFrame): IntArray {
        val grid = IntArray(GRID_W * GRID_H)
        val cellW = frame.width / GRID_W
        val cellH = frame.height / GRID_H
        for (gy in 0 until GRID_H) {
            for (gx in 0 until GRID_W) {
                var sum = 0
                var count = 0
                var y = gy * cellH
                while (y < (gy + 1) * cellH) {
                    var x = gx * cellW
                    val rowBase = y * frame.width
                    while (x < (gx + 1) * cellW) {
                        sum += frame.nv21[rowBase + x].toInt() and 0xFF
                        count++
                        x += 4
                    }
                    y += 4
                }
                grid[gy * GRID_W + gx] = if (count == 0) 0 else sum / count
            }
        }
        return grid
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :packages:camera:testDebugUnitTest --tests "com.superdash.camera.FrameDiffMotionDetectorTest"`
Expected: PASS (6 tests). If the sensitivity boundary tests fail, check the block-size arithmetic against GRID cell counts — a 40px block in 320x240 covers 4x4=16 of 768 cells ≈ 2.1%, which must sit between `MIN_REQUIRED_FRACTION` (0.5%) and `MAX_REQUIRED_FRACTION` (20%).

- [ ] **Step 6: Commit**

```bash
./gradlew ktlintFormat
git add settings.gradle.kts gradle/libs.versions.toml packages/camera
git commit -m "feat(camera): add camera module with frame-diff motion detector"
```

---

### Task 2: `MotionGate` clear-delay hold

**Files:**
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/MotionGate.kt`
- Test: `packages/camera/src/test/kotlin/com/superdash/camera/MotionGateTest.kt`

**Interfaces:**
- Produces: `class MotionGate(clearDelayMs: () -> Long) { fun update(detected: Boolean, nowMs: Long): Boolean }` — pure, time injected by caller.

- [ ] **Step 1: Write the failing tests**

Create `packages/camera/src/test/kotlin/com/superdash/camera/MotionGateTest.kt`:

```kotlin
package com.superdash.camera

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionGateTest {
    @Test
    fun `inactive until first detection`() {
        val gate = MotionGate(clearDelayMs = { 10_000L })
        assertFalse(gate.update(detected = false, nowMs = 0L))
    }

    @Test
    fun `detection activates and holds for the clear delay`() {
        val gate = MotionGate(clearDelayMs = { 10_000L })
        assertTrue(gate.update(detected = true, nowMs = 0L))
        assertTrue(gate.update(detected = false, nowMs = 9_999L))
        assertFalse(gate.update(detected = false, nowMs = 10_000L))
    }

    @Test
    fun `new detection extends the hold`() {
        val gate = MotionGate(clearDelayMs = { 10_000L })
        gate.update(detected = true, nowMs = 0L)
        gate.update(detected = true, nowMs = 8_000L)
        assertTrue(gate.update(detected = false, nowMs = 17_999L))
        assertFalse(gate.update(detected = false, nowMs = 18_000L))
    }

    @Test
    fun `clear delay is read at detection time`() {
        var delay = 10_000L
        val gate = MotionGate(clearDelayMs = { delay })
        gate.update(detected = true, nowMs = 0L)
        delay = 1_000L
        // Existing hold keeps the old deadline; the new delay applies on next detection.
        assertTrue(gate.update(detected = false, nowMs = 5_000L))
        gate.update(detected = true, nowMs = 20_000L)
        assertFalse(gate.update(detected = false, nowMs = 21_000L))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :packages:camera:testDebugUnitTest --tests "com.superdash.camera.MotionGateTest"`
Expected: FAIL — `MotionGate` unresolved.

- [ ] **Step 3: Implement**

Create `packages/camera/src/main/kotlin/com/superdash/camera/MotionGate.kt`:

```kotlin
package com.superdash.camera

/** Debounces per-frame detector output into a stable motion state: a
 *  detection turns the gate on and holds it for the configured clear delay,
 *  refreshed by every new detection. */
class MotionGate(
    private val clearDelayMs: () -> Long,
) {
    private var activeUntilMs = Long.MIN_VALUE

    fun update(
        detected: Boolean,
        nowMs: Long,
    ): Boolean {
        if (detected) {
            activeUntilMs = nowMs + clearDelayMs()
        }
        return nowMs < activeUntilMs
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :packages:camera:testDebugUnitTest --tests "com.superdash.camera.MotionGateTest"`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add packages/camera
git commit -m "feat(camera): add motion gate with clear-delay hold"
```

---

### Task 3: `CameraPipeline` interface + `CameraController`

**Files:**
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/CameraPipeline.kt`
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/CameraSettings.kt`
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/CameraController.kt`
- Test: `packages/camera/src/test/kotlin/com/superdash/camera/CameraControllerTest.kt`

**Interfaces:**
- Consumes: `MotionDetector`, `MotionGate`, `CameraFrame` from Tasks 1–2.
- Produces:
  - `sealed class CameraAvailability { Off, Running, PermissionMissing, Error(message) }`
  - `class CameraPipelineConfig(val width: Int, val height: Int, val facingFront: Boolean)`
  - `interface CameraPipeline { val frames: Flow<CameraFrame>; val availability: StateFlow<CameraAvailability>; fun start(config: CameraPipelineConfig); fun stop(); fun encodeJpeg(frame: CameraFrame, quality: Int): ByteArray? }`
  - `interface CameraSettings` (full definition below — implemented by app in Task 9)
  - `class CameraController(...)` exposing `motionActive: StateFlow<Boolean>`, `activeMotionMode: StateFlow<String>`, `availability: StateFlow<CameraAvailability>`, `jpegFrames: Flow<ByteArray>`, `suspend fun latestJpeg(): ByteArray?`

- [ ] **Step 1: Create the pipeline and settings interfaces**

Create `packages/camera/src/main/kotlin/com/superdash/camera/CameraPipeline.kt`:

```kotlin
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
```

Create `packages/camera/src/main/kotlin/com/superdash/camera/CameraSettings.kt`:

```kotlin
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
```

- [ ] **Step 2: Write the failing tests**

Create `packages/camera/src/test/kotlin/com/superdash/camera/CameraControllerTest.kt`:

```kotlin
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

    override fun start(config: CameraPipelineConfig) {
        started = config
        availabilityState.value = CameraAvailability.Running
    }

    override fun stop() {
        stopCount++
        started = null
        availabilityState.value = CameraAvailability.Off
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
    val jpegQualityState = MutableStateFlow(60)
    val motionModeState = MutableStateFlow("off")
    val motionSensitivityState = MutableStateFlow(50)
    val motionClearDelaySecState = MutableStateFlow(15)
    val wakeOnMotionState = MutableStateFlow(false)

    override val enabled: Flow<Boolean> = enabledState
    override val facing: Flow<String> = facingState
    override val resolution: Flow<String> = resolutionState
    override val jpegQuality: Flow<Int> = jpegQualityState
    override val motionMode: Flow<String> = motionModeState
    override val motionSensitivity: Flow<Int> = motionSensitivityState
    override val motionClearDelaySec: Flow<Int> = motionClearDelaySecState
    override val wakeOnMotion: Flow<Boolean> = wakeOnMotionState

    override suspend fun setEnabled(value: Boolean) = enabledState.emit(value)

    override suspend fun setFacing(value: String) = facingState.emit(value)

    override suspend fun setResolution(value: String) = resolutionState.emit(value)

    override suspend fun setJpegQuality(value: Int) = jpegQualityState.emit(value)

    override suspend fun setMotionMode(value: String) = motionModeState.emit(value)

    override suspend fun setMotionSensitivity(value: Int) = motionSensitivityState.emit(value)

    override suspend fun setMotionClearDelaySec(value: Int) = motionClearDelaySecState.emit(value)

    override suspend fun setWakeOnMotion(value: Boolean) = wakeOnMotionState.emit(value)
}

/** Detector scripted to a fixed answer per frame. */
private class ScriptedDetector(
    private val answer: () -> Boolean,
) : MotionDetector {
    var processed = 0

    override suspend fun process(frame: CameraFrame): Boolean {
        processed++
        return answer()
    }

    override fun reset() {}
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
            settings.jpegQualityState.value = 42
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
            assertEquals(listOf<Byte>(42, 7), controller.latestJpeg()?.toList())
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
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :packages:camera:testDebugUnitTest --tests "com.superdash.camera.CameraControllerTest"`
Expected: FAIL — `CameraController` unresolved.

- [ ] **Step 4: Implement**

Create `packages/camera/src/main/kotlin/com/superdash/camera/CameraController.kt`:

```kotlin
package com.superdash.camera

import com.superdash.core.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val log = Log("CameraController")

/** Minimum ms between JPEG stream frames (~10 fps cap). */
internal const val STREAM_MIN_INTERVAL_MS = 100L

private const val DEFAULT_RESOLUTION = "1280x720"

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

    private val quality: StateFlow<Int> =
        settings.jpegQuality.stateIn(scope, SharingStarted.Eagerly, 60)

    private val clearDelaySec: StateFlow<Int> =
        settings.motionClearDelaySec.stateIn(scope, SharingStarted.Eagerly, 15)

    init {
        scope.launch { runPipelineControl() }
        scope.launch { cacheAndEncodeFrames() }
        scope.launch { runMotionDetection() }
    }

    suspend fun latestJpeg(): ByteArray? =
        latestFrame.value?.let { frame -> pipeline.encodeJpeg(frame, quality.value) }

    private suspend fun runPipelineControl() {
        combine(settings.enabled, settings.resolution, settings.facing) { enabled, resolution, facing ->
            Triple(enabled, resolution, facing)
        }.distinctUntilChanged().collect { (enabled, resolution, facing) ->
            if (enabled) {
                val (width, height) = parseResolution(resolution)
                pipeline.start(CameraPipelineConfig(width, height, facingFront = facing != "back"))
            } else {
                pipeline.stop()
                latestFrame.value = null
                motionActiveState.value = false
            }
        }
    }

    private suspend fun cacheAndEncodeFrames() {
        var lastStreamEmitMs = Long.MIN_VALUE
        pipeline.frames.collect { frame ->
            latestFrame.value = frame
            val now = nowMs()
            if (jpegFramesFlow.subscriptionCount.value > 0 &&
                now - lastStreamEmitMs >= STREAM_MIN_INTERVAL_MS
            ) {
                pipeline.encodeJpeg(frame, quality.value)?.let { jpeg ->
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
            pipeline.frames.collect { frame ->
                val detected =
                    runCatching { detector.process(frame) }
                        .onFailure { log.w("motion detector failed", it) }
                        .getOrDefault(false)
                motionActiveState.value = gate.update(detected, nowMs())
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :packages:camera:testDebugUnitTest`
Expected: PASS (all camera module tests). Note the jpegFrames rate-limit test relies on `subscriptionCount` being observed after the background collector starts — `UnconfinedTestDispatcher` makes the launch run eagerly.

- [ ] **Step 6: Commit**

```bash
./gradlew ktlintFormat
git add packages/camera
git commit -m "feat(camera): add camera controller orchestrating pipeline, detection, and jpeg stream"
```

---

### Task 4: `Nv21Rotator` + `CameraXPipeline` hardware adapter

**Files:**
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/Nv21Rotator.kt`
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/CameraXPipeline.kt`
- Modify: `packages/camera/build.gradle.kts` (add CameraX deps)
- Test: `packages/camera/src/test/kotlin/com/superdash/camera/Nv21RotatorTest.kt`

**Interfaces:**
- Consumes: `CameraPipeline`, `CameraPipelineConfig`, `CameraAvailability`, `CameraFrame` from Task 3.
- Produces: `class CameraXPipeline(context: Context) : CameraPipeline`; `object Nv21Rotator { fun rotate(nv21: ByteArray, width: Int, height: Int, degrees: Int): Rotated }` with `class Rotated(val nv21: ByteArray, val width: Int, val height: Int)`.

- [ ] **Step 1: Add CameraX dependencies**

In `packages/camera/build.gradle.kts` dependencies block add:

```kotlin
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
```

- [ ] **Step 2: Write the failing rotator tests**

Create `packages/camera/src/test/kotlin/com/superdash/camera/Nv21RotatorTest.kt`:

```kotlin
package com.superdash.camera

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class Nv21RotatorTest {
    // 4x2 NV21: Y = row-major 0..7, chroma = one VU pair per 2x2 block.
    private val nv21 =
        byteArrayOf(
            0, 1, 2, 3,
            4, 5, 6, 7,
            100, 101, 102, 103, // V0 U0 V1 U1
        )

    @Test
    fun `rotate 0 returns the same buffer`() {
        val rotated = Nv21Rotator.rotate(nv21, width = 4, height = 2, degrees = 0)
        assertSame(nv21, rotated.nv21)
        assertEquals(4, rotated.width)
        assertEquals(2, rotated.height)
    }

    @Test
    fun `rotate 90 swaps dimensions and remaps luma clockwise`() {
        val rotated = Nv21Rotator.rotate(nv21, width = 4, height = 2, degrees = 90)
        assertEquals(2, rotated.width)
        assertEquals(4, rotated.height)
        // Clockwise: new(x, y) = old(y', x') with newY0 row = old first column bottom-up.
        assertArrayEquals(
            byteArrayOf(4, 0, 5, 1, 6, 2, 7, 3),
            rotated.nv21.copyOfRange(0, 8),
        )
    }

    @Test
    fun `rotate 180 reverses luma`() {
        val rotated = Nv21Rotator.rotate(nv21, width = 4, height = 2, degrees = 180)
        assertEquals(4, rotated.width)
        assertEquals(2, rotated.height)
        assertArrayEquals(
            byteArrayOf(7, 6, 5, 4, 3, 2, 1, 0),
            rotated.nv21.copyOfRange(0, 8),
        )
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :packages:camera:testDebugUnitTest --tests "com.superdash.camera.Nv21RotatorTest"`
Expected: FAIL — `Nv21Rotator` unresolved.

- [ ] **Step 4: Implement the rotator**

Create `packages/camera/src/main/kotlin/com/superdash/camera/Nv21Rotator.kt`:

```kotlin
package com.superdash.camera

/** Rotates NV21 buffers so JPEG encoding produces upright images. Pure
 *  byte-shuffling; no Android APIs, so it stays JVM-testable. */
object Nv21Rotator {
    class Rotated(
        val nv21: ByteArray,
        val width: Int,
        val height: Int,
    )

    fun rotate(
        nv21: ByteArray,
        width: Int,
        height: Int,
        degrees: Int,
    ): Rotated {
        val normalized = ((degrees % 360) + 360) % 360
        if (normalized == 0) {
            return Rotated(nv21, width, height)
        }
        val ySize = width * height
        val out = ByteArray(nv21.size)
        val (outW, outH) =
            when (normalized) {
                90, 270 -> height to width
                else -> width to height
            }
        // Luma.
        for (y in 0 until height) {
            for (x in 0 until width) {
                val (nx, ny) =
                    when (normalized) {
                        90 -> (height - 1 - y) to x
                        180 -> (width - 1 - x) to (height - 1 - y)
                        else -> y to (width - 1 - x) // 270
                    }
                out[ny * outW + nx] = nv21[y * width + x]
            }
        }
        // Chroma: one interleaved VU pair per 2x2 block; rotate block-wise.
        val chromaW = width / 2
        val chromaH = height / 2
        val outChromaW = outW / 2
        for (cy in 0 until chromaH) {
            for (cx in 0 until chromaW) {
                val (ncx, ncy) =
                    when (normalized) {
                        90 -> (chromaH - 1 - cy) to cx
                        180 -> (chromaW - 1 - cx) to (chromaH - 1 - cy)
                        else -> cy to (chromaW - 1 - cx) // 270
                    }
                val src = ySize + (cy * chromaW + cx) * 2
                val dst = ySize + (ncy * outChromaW + ncx) * 2
                out[dst] = nv21[src]
                out[dst + 1] = nv21[src + 1]
            }
        }
        return Rotated(out, outW, outH)
    }
}
```

- [ ] **Step 5: Run rotator tests to verify they pass**

Run: `./gradlew :packages:camera:testDebugUnitTest --tests "com.superdash.camera.Nv21RotatorTest"`
Expected: PASS (3 tests).

- [ ] **Step 6: Implement `CameraXPipeline` (thin adapter, verified by compilation + Task 11 on-device)**

Create `packages/camera/src/main/kotlin/com/superdash/camera/CameraXPipeline.kt`:

```kotlin
package com.superdash.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Handler
import android.os.Looper
import android.util.Size
import androidx.camera.core.CameraSelector
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

    override fun start(config: CameraPipelineConfig) {
        mainHandler.post {
            wantedConfig = config
            retryDelayMs = INITIAL_RETRY_DELAY_MS
            startOnMain(config)
        }
    }

    override fun stop() {
        mainHandler.post {
            wantedConfig = null
            stopOnMain()
        }
    }

    /** Camera-busy / transient failures: retry with exponential backoff while
     *  the pipeline is still wanted. */
    private fun scheduleRetry() {
        val delay = retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
        mainHandler.postDelayed({
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
                val analysis =
                    ImageAnalysis
                        .Builder()
                        .setTargetResolution(Size(config.width, config.height))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                analysis.setAnalyzer(analysisExecutor) { imageProxy -> onFrame(imageProxy) }
                val selector =
                    if (config.facingFront) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(owner, selector, analysis)
                owner.moveTo(Lifecycle.State.RESUMED)
                availabilityState.value = CameraAvailability.Running
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

    private fun onFrame(imageProxy: ImageProxy) {
        try {
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
```

- [ ] **Step 7: Compile and run module tests**

Run: `./gradlew :packages:camera:testDebugUnitTest`
Expected: PASS. The CameraX adapter has no unit tests (hardware adapter — verified on-device in Task 11).

- [ ] **Step 8: Commit**

```bash
./gradlew ktlintFormat
git add packages/camera
git commit -m "feat(camera): add CameraX pipeline adapter and NV21 rotation"
```

---

### Task 5: `PersonMotionDetector` (ML Kit)

**Files:**
- Create: `packages/camera/src/main/kotlin/com/superdash/camera/PersonMotionDetector.kt`
- Modify: `packages/camera/build.gradle.kts` (add ML Kit + play-services coroutines deps)

**Interfaces:**
- Consumes: `MotionDetector`, `CameraFrame` from Task 1.
- Produces: `class PersonMotionDetector(minIntervalMs: Long = 500, nowMs: () -> Long = System::currentTimeMillis) : MotionDetector`.

- [ ] **Step 1: Add dependencies**

In `packages/camera/build.gradle.kts` dependencies block add:

```kotlin
    implementation(libs.mlkit.face.detection)
    implementation(libs.kotlinx.coroutines.play.services)
```

- [ ] **Step 2: Implement**

Create `packages/camera/src/main/kotlin/com/superdash/camera/PersonMotionDetector.kt`:

```kotlin
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
```

Note: the FIRST `process()` call constructs the lazy detector — if ML Kit is unavailable that throws inside `process()`, which `CameraController.runMotionDetection` already catches per-frame. To make fallback happen at mode-selection time instead (as tested in Task 3), the app-side factory in Task 9 wires `"person" to { PersonMotionDetector().also { /* touch nothing */ } }` — construction itself is cheap and safe; runtime failures degrade to "no detection" with a logged warning. This is the intended behavior split: construction failures → mode fallback; runtime failures → logged, last result reused.

- [ ] **Step 3: Compile**

Run: `./gradlew :packages:camera:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
./gradlew ktlintFormat
git add packages/camera
git commit -m "feat(camera): add ML Kit person detector for person motion mode"
```

---

### Task 6: ESPHome camera message ids + image chunker

**Files:**
- Modify: `packages/esphome-server/src/main/kotlin/com/superdash/esphome/EsphomeMessageType.kt`
- Create: `packages/esphome-server/src/main/kotlin/com/superdash/esphome/EsphomeCameraChunker.kt`
- Test: `packages/esphome-server/src/test/kotlin/com/superdash/esphome/EsphomeCameraChunkerTest.kt`

**Interfaces:**
- Consumes: generated `org.esphome.api.CameraImageResponse` (already exists — full `api.proto` is compiled).
- Produces: `EsphomeMessageType.LIST_ENTITIES_CAMERA_RESPONSE = 43`, `CAMERA_IMAGE_RESPONSE = 44`, `CAMERA_IMAGE_REQUEST = 45`; `internal const val CAMERA_CHUNK_BYTES = 15 * 1024`; `internal fun cameraImageChunks(key: Int, jpeg: ByteArray, chunkBytes: Int = CAMERA_CHUNK_BYTES): List<CameraImageResponse>`.

- [ ] **Step 1: Write the failing tests**

Create `packages/esphome-server/src/test/kotlin/com/superdash/esphome/EsphomeCameraChunkerTest.kt`:

```kotlin
package com.superdash.esphome

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EsphomeCameraChunkerTest {
    @Test
    fun `empty image produces no chunks`() {
        assertEquals(0, cameraImageChunks(key = 1, jpeg = ByteArray(0)).size)
    }

    @Test
    fun `small image is a single done chunk`() {
        val jpeg = ByteArray(100) { it.toByte() }
        val chunks = cameraImageChunks(key = 7, jpeg = jpeg)
        assertEquals(1, chunks.size)
        assertEquals(7, chunks[0].key)
        assertTrue(chunks[0].done)
        assertArrayEquals(jpeg, chunks[0].data.toByteArray())
    }

    @Test
    fun `large image is split with done only on the last chunk`() {
        val jpeg = ByteArray(10) { it.toByte() }
        val chunks = cameraImageChunks(key = 1, jpeg = jpeg, chunkBytes = 4)
        assertEquals(3, chunks.size)
        assertFalse(chunks[0].done)
        assertFalse(chunks[1].done)
        assertTrue(chunks[2].done)
        assertArrayEquals(
            jpeg,
            chunks.flatMap { it.data.toByteArray().toList() }.toByteArray(),
        )
        assertEquals(2, chunks[2].data.size())
    }

    @Test
    fun `exact multiple has full final chunk marked done`() {
        val chunks = cameraImageChunks(key = 1, jpeg = ByteArray(8), chunkBytes = 4)
        assertEquals(2, chunks.size)
        assertTrue(chunks[1].done)
        assertEquals(4, chunks[1].data.size())
    }

    @Test
    fun `default chunk size keeps messages under the noise payload cap`() {
        // 12 bytes of proto overhead headroom: key(fixed32)+tags+len+done.
        assertTrue(CAMERA_CHUNK_BYTES + 32 <= NOISE_MAX_PAYLOAD)
        val chunks = cameraImageChunks(key = Int.MAX_VALUE, jpeg = ByteArray(CAMERA_CHUNK_BYTES + 1))
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].toByteArray().size <= NOISE_MAX_PAYLOAD)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :packages:esphome-server:testDebugUnitTest --tests "com.superdash.esphome.EsphomeCameraChunkerTest"`
Expected: FAIL — `cameraImageChunks` unresolved.

- [ ] **Step 3: Implement**

Add to `packages/esphome-server/src/main/kotlin/com/superdash/esphome/EsphomeMessageType.kt`, after the button constants:

```kotlin

    const val LIST_ENTITIES_CAMERA_RESPONSE = 43
    const val CAMERA_IMAGE_RESPONSE = 44
    const val CAMERA_IMAGE_REQUEST = 45
```

Create `packages/esphome-server/src/main/kotlin/com/superdash/esphome/EsphomeCameraChunker.kt`:

```kotlin
package com.superdash.esphome

import com.google.protobuf.ByteString
import org.esphome.api.CameraImageResponse

/** Max image bytes per CameraImageResponse. A whole message (proto overhead
 *  included) must fit one noise frame: NOISE_MAX_PAYLOAD (16 KiB) minus the
 *  4-byte inner header, so 15 KiB of data leaves ample headroom. aioesphomeapi
 *  reassembles chunks until `done = true`. */
internal const val CAMERA_CHUNK_BYTES: Int = 15 * 1024

internal fun cameraImageChunks(
    key: Int,
    jpeg: ByteArray,
    chunkBytes: Int = CAMERA_CHUNK_BYTES,
): List<CameraImageResponse> {
    require(chunkBytes > 0) { "chunkBytes must be positive" }
    if (jpeg.isEmpty()) {
        return emptyList()
    }
    val chunks = ArrayList<CameraImageResponse>((jpeg.size + chunkBytes - 1) / chunkBytes)
    var offset = 0
    while (offset < jpeg.size) {
        val end = minOf(offset + chunkBytes, jpeg.size)
        chunks.add(
            CameraImageResponse
                .newBuilder()
                .setKey(key)
                .setData(ByteString.copyFrom(jpeg, offset, end - offset))
                .setDone(end == jpeg.size)
                .build(),
        )
        offset = end
    }
    return chunks
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :packages:esphome-server:testDebugUnitTest --tests "com.superdash.esphome.EsphomeCameraChunkerTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add packages/esphome-server
git commit -m "feat(esphome): add camera message ids and image chunker"
```

---

### Task 7: `Camera` entity, BinarySensor `deviceClass`, connection handling

**Files:**
- Modify: `packages/esphome-server/src/main/kotlin/com/superdash/esphome/EsphomeEntities.kt`
- Modify: `packages/esphome-server/src/main/kotlin/com/superdash/esphome/EsphomeConnection.kt`
- Test: `packages/esphome-server/src/test/kotlin/com/superdash/esphome/EsphomeCameraConnectionTest.kt`

**Interfaces:**
- Consumes: `cameraImageChunks`, message ids from Task 6; existing `EsphomeConnection`, `EsphomeTransport`, test helpers `writeEsphomeFrame`/`readEsphomeFrame` (see `EsphomeConnectionTest.kt`).
- Produces:
  - `EsphomeEntity.BinarySensor` gains `val deviceClass: String = ""` (sent in ListEntities).
  - `EsphomeEntity.Camera(key, objectId, name, frames: Flow<ByteArray>, latestJpeg: suspend () -> ByteArray?)`.
  - `EsphomeConnection` handles `CAMERA_IMAGE_REQUEST` (single + stream, ~5 s stream window refreshed per request) and lists cameras; constructor gains `nanoTime: () -> Long = System::nanoTime`.

- [ ] **Step 1: Add the entity types**

In `EsphomeEntities.kt`, change `BinarySensor` to:

```kotlin
    data class BinarySensor(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val state: Flow<Boolean>,
        val deviceClass: String = "",
    ) : EsphomeEntity()
```

Add after `Button`:

```kotlin

    /** ESPHome camera. HA pulls JPEGs via CameraImageRequest: `single` sends
     *  the latest cached frame once; `stream` opens a ~5s window (refreshed by
     *  repeated requests) during which every frame from [frames] is pushed.
     *  Images are chunked into ≤15KiB CameraImageResponse messages. */
    data class Camera(
        override val key: Int,
        override val objectId: String,
        override val name: String,
        val frames: Flow<ByteArray>,
        val latestJpeg: suspend () -> ByteArray?,
    ) : EsphomeEntity()
```

- [ ] **Step 2: Write the failing connection tests**

Create `packages/esphome-server/src/test/kotlin/com/superdash/esphome/EsphomeCameraConnectionTest.kt`. Follow the ByteChannel pattern from `EsphomeConnectionTest.kt` (hello first, then drive frames):

```kotlin
package com.superdash.esphome

import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.esphome.api.CameraImageRequest
import org.esphome.api.CameraImageResponse
import org.esphome.api.HelloRequest
import org.esphome.api.ListEntitiesBinarySensorResponse
import org.esphome.api.ListEntitiesCameraResponse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EsphomeCameraConnectionTest {
    private class Harness(
        scope: TestScope,
        entities: List<EsphomeEntity>,
        nanoTime: () -> Long,
    ) {
        val clientToServer = ByteChannel(autoFlush = true)
        val serverToClient = ByteChannel(autoFlush = true)
        val connection =
            EsphomeConnection(
                transport = PlainTransport(input = clientToServer, output = serverToClient),
                deviceInfo =
                    EsphomeDeviceInfo(
                        name = "superdash-test",
                        macAddress = "AA:BB:CC:DD:EE:FF",
                        esphomeVersion = "2026.4.5-superdash",
                        compilationTime = "",
                        model = "Pixel emulator",
                        manufacturer = "Google",
                        friendlyName = "Superdash Test",
                    ),
                entities = entities,
                nanoTime = nanoTime,
            )
        val job = scope.launch { connection.run() }

        suspend fun hello() {
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest.newBuilder().setClientInfo("test").build().toByteArray(),
            )
            serverToClient.readEsphomeFrame() // HelloResponse
        }

        suspend fun requestImage(
            single: Boolean,
            stream: Boolean,
        ) {
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.CAMERA_IMAGE_REQUEST,
                CameraImageRequest.newBuilder().setSingle(single).setStream(stream).build().toByteArray(),
            )
        }

        /** Reads CameraImageResponse chunks until done and reassembles. */
        suspend fun readImage(): ByteArray {
            val bytes = ArrayList<Byte>()
            while (true) {
                val frame = serverToClient.readEsphomeFrame()
                assertEquals(EsphomeMessageType.CAMERA_IMAGE_RESPONSE, frame.messageType)
                val chunk = CameraImageResponse.parseFrom(frame.payload)
                bytes.addAll(chunk.data.toByteArray().toList())
                if (chunk.done) {
                    return bytes.toByteArray()
                }
            }
        }
    }

    private fun cameraEntity(
        frames: MutableSharedFlow<ByteArray>,
        latest: ByteArray?,
    ): EsphomeEntity.Camera =
        EsphomeEntity.Camera(
            key = keyFromObjectId("camera"),
            objectId = "camera",
            name = "Camera",
            frames = frames,
            latestJpeg = { latest },
        )

    @Test
    fun `list entities includes camera and binary sensor device class`() =
        runTest(UnconfinedTestDispatcher()) {
            val frames = MutableSharedFlow<ByteArray>()
            val entities =
                listOf(
                    EsphomeEntity.BinarySensor(
                        key = keyFromObjectId("motion"),
                        objectId = "motion",
                        name = "Motion",
                        state = MutableSharedFlow(),
                        deviceClass = "motion",
                    ),
                    cameraEntity(frames, latest = null),
                )
            val harness = Harness(this, entities, nanoTime = { 0L })
            harness.hello()
            harness.clientToServer.writeEsphomeFrame(EsphomeMessageType.LIST_ENTITIES_REQUEST, ByteArray(0))

            val binary = harness.serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_BINARY_SENSOR_RESPONSE, binary.messageType)
            assertEquals("motion", ListEntitiesBinarySensorResponse.parseFrom(binary.payload).deviceClass)

            val camera = harness.serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_CAMERA_RESPONSE, camera.messageType)
            val parsed = ListEntitiesCameraResponse.parseFrom(camera.payload)
            assertEquals("camera", parsed.objectId)
            assertEquals("Camera", parsed.name)

            val done = harness.serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_DONE_RESPONSE, done.messageType)
            harness.job.cancel()
        }

    @Test
    fun `single request sends the latest jpeg chunked`() =
        runTest(UnconfinedTestDispatcher()) {
            val jpeg = ByteArray(40_000) { it.toByte() } // > 2 chunks at 15 KiB
            val harness =
                Harness(this, listOf(cameraEntity(MutableSharedFlow(), latest = jpeg)), nanoTime = { 0L })
            harness.hello()
            harness.requestImage(single = true, stream = false)
            assertArrayEquals(jpeg, harness.readImage())
            harness.job.cancel()
        }

    @Test
    fun `stream request pushes frames until the window closes`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            val frames = MutableSharedFlow<ByteArray>()
            val harness = Harness(this, listOf(cameraEntity(frames, latest = null)), nanoTime = { now })
            harness.hello()
            harness.requestImage(single = false, stream = true)

            frames.emit(byteArrayOf(1, 2, 3))
            assertArrayEquals(byteArrayOf(1, 2, 3), harness.readImage())

            // Past the 5s window: the next frame ends the stream job, nothing is sent.
            now = 6_000_000_000L
            frames.emit(byteArrayOf(4, 5, 6))
            assertEquals(0, frames.subscriptionCount.value)
            harness.job.cancel()
        }

    @Test
    fun `stream request refresh extends the window`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            val frames = MutableSharedFlow<ByteArray>()
            val harness = Harness(this, listOf(cameraEntity(frames, latest = null)), nanoTime = { now })
            harness.hello()
            harness.requestImage(single = false, stream = true)
            frames.emit(byteArrayOf(1))
            harness.readImage()

            now = 4_000_000_000L
            harness.requestImage(single = false, stream = true) // refresh at t=4s → window to t=9s
            now = 8_000_000_000L
            frames.emit(byteArrayOf(2))
            assertArrayEquals(byteArrayOf(2), harness.readImage())
            harness.job.cancel()
        }

    @Test
    fun `single request with no cached frame sends nothing and keeps connection alive`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness =
                Harness(this, listOf(cameraEntity(MutableSharedFlow(), latest = null)), nanoTime = { 0L })
            harness.hello()
            harness.requestImage(single = true, stream = false)
            // Connection still answers pings afterwards.
            harness.clientToServer.writeEsphomeFrame(EsphomeMessageType.PING_REQUEST, ByteArray(0))
            assertEquals(EsphomeMessageType.PING_RESPONSE, harness.serverToClient.readEsphomeFrame().messageType)
            assertTrue(harness.job.isActive)
            harness.job.cancel()
        }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :packages:esphome-server:testDebugUnitTest --tests "com.superdash.esphome.EsphomeCameraConnectionTest"`
Expected: FAIL — no `nanoTime` parameter, no `Camera` handling (compile errors).

- [ ] **Step 4: Implement connection handling**

In `EsphomeConnection.kt`:

1. Add imports: `org.esphome.api.CameraImageRequest`, `org.esphome.api.ListEntitiesCameraResponse`, `kotlinx.coroutines.flow.takeWhile`.
2. Add below `DEFAULT_IDLE_TIMEOUT_MS`:

```kotlin
/** How long a CameraImageRequest(stream) keeps the frame push alive. HA
 *  refreshes the window with repeated stream requests while a client watches. */
internal const val CAMERA_STREAM_WINDOW_NANOS = 5_000_000_000L
```

3. Add constructor parameter and fields:

```kotlin
internal class EsphomeConnection(
    private val transport: EsphomeTransport,
    private val deviceInfo: EsphomeDeviceInfo,
    private val entities: List<EsphomeEntity>,
    private val idleTimeoutMs: Long = DEFAULT_IDLE_TIMEOUT_MS,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private var helloDone = false
    private var stateJobs: List<Job> = emptyList()
    private var cameraStreamJob: Job? = null

    @Volatile private var cameraStreamDeadlineNanos = Long.MIN_VALUE
```

4. In `run()`'s `finally`, cancel the stream job too:

```kotlin
            } finally {
                stateJobs.forEach { it.cancel() }
                cameraStreamJob?.cancel()
            }
```

5. In `runUntilDisconnect`'s `when`, add before the `else` branch:

```kotlin
                EsphomeMessageType.CAMERA_IMAGE_REQUEST -> handleCameraImageRequest(frame.payload, scope)
```

6. In `handleListEntities`'s `when (entity)`, add a branch:

```kotlin
                is EsphomeEntity.Camera -> {
                    val msg =
                        ListEntitiesCameraResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_CAMERA_RESPONSE, msg.toByteArray())
                }
```

And extend the BinarySensor branch to include the device class:

```kotlin
                is EsphomeEntity.BinarySensor -> {
                    val msg =
                        ListEntitiesBinarySensorResponse
                            .newBuilder()
                            .setObjectId(entity.objectId)
                            .setKey(entity.key)
                            .setName(entity.name)
                            .setDeviceClass(entity.deviceClass)
                            .build()
                    transport.writeFrame(EsphomeMessageType.LIST_ENTITIES_BINARY_SENSOR_RESPONSE, msg.toByteArray())
                }
```

7. In `subscribeStates`'s `when (entity)`, add: `is EsphomeEntity.Camera -> null` (frames are pushed on request, not on state subscription).

8. Add the handlers at the end of the class:

```kotlin
    private suspend fun handleCameraImageRequest(
        payload: ByteArray,
        scope: CoroutineScope,
    ) {
        val request = CameraImageRequest.parseFrom(payload)
        val camera = entities.filterIsInstance<EsphomeEntity.Camera>().firstOrNull()
        if (camera == null) {
            log.w("camera image request but no camera entity")
            return
        }
        if (request.single) {
            val jpeg =
                runCatching { camera.latestJpeg() }
                    .onFailure { log.w("latestJpeg failed", it) }
                    .getOrNull()
            if (jpeg == null) {
                log.i("no camera frame available for single request")
            } else {
                sendCameraImage(camera.key, jpeg)
            }
        }
        if (request.stream) {
            cameraStreamDeadlineNanos = nanoTime() + CAMERA_STREAM_WINDOW_NANOS
            if (cameraStreamJob?.isActive != true) {
                cameraStreamJob =
                    scope.launch {
                        camera.frames
                            .takeWhile { nanoTime() < cameraStreamDeadlineNanos }
                            .collect { jpeg -> sendCameraImage(camera.key, jpeg) }
                    }
            }
        }
    }

    private suspend fun sendCameraImage(
        key: Int,
        jpeg: ByteArray,
    ) {
        for (chunk in cameraImageChunks(key, jpeg)) {
            transport.writeFrame(EsphomeMessageType.CAMERA_IMAGE_RESPONSE, chunk.toByteArray())
        }
    }
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :packages:esphome-server:testDebugUnitTest`
Expected: PASS — the new camera tests AND all existing tests (the `deviceClass` default `""` keeps existing `BinarySensor` constructions compiling; proto3 default-empty strings keep existing list-entities assertions passing).

- [ ] **Step 6: Commit**

```bash
./gradlew ktlintFormat
git add packages/esphome-server
git commit -m "feat(esphome): handle camera entity with single and stream image requests"
```

---

### Task 8: `EsphomeCameraBindings` + entity catalog

**Files:**
- Modify: `packages/esphome-server/src/main/kotlin/com/superdash/esphome/EsphomeFeatureBindings.kt`
- Modify: `packages/esphome-server/src/main/kotlin/com/superdash/esphome/EsphomeBindings.kt`
- Test: `packages/esphome-server/src/test/kotlin/com/superdash/esphome/EsphomeBindingsTest.kt` (update)

**Interfaces:**
- Consumes: `EsphomeEntity.Camera`, `binarySensorEntity` from Task 7.
- Produces: `data class EsphomeCameraBindings(...)` (exact fields below); `esphomeEntities(...)` and `EsphomeBindings(...)` gain a required `camera: EsphomeCameraBindings` parameter. New objectIds: `camera_enabled`, `wake_on_motion`, `motion`, `motion_detection_mode`, `motion_sensitivity`, `motion_clear_delay_sec`, `camera`.

- [ ] **Step 1: Add the bindings data class**

In `EsphomeFeatureBindings.kt`, after `EsphomeDoorbellBindings`:

```kotlin
/** Tablet camera: enable switch, motion sensor and tuning, wake-on-motion,
 *  and JPEG frame sources for the ESPHome camera entity. */
data class EsphomeCameraBindings(
    val cameraEnabled: Flow<Boolean>,
    val setCameraEnabled: suspend (Boolean) -> Unit,
    val motionDetected: Flow<Boolean>,
    val motionMode: Flow<String>,
    val setMotionMode: suspend (String) -> Unit,
    val motionSensitivity: Flow<Float>,
    val setMotionSensitivity: suspend (Float) -> Unit,
    val motionClearDelaySec: Flow<Float>,
    val setMotionClearDelaySec: suspend (Float) -> Unit,
    val wakeOnMotion: Flow<Boolean>,
    val setWakeOnMotion: suspend (Boolean) -> Unit,
    val jpegFrames: Flow<ByteArray>,
    val latestJpeg: suspend () -> ByteArray?,
)
```

- [ ] **Step 2: Update the failing test first**

In `EsphomeBindingsTest.kt`:
1. Add a `camera = EsphomeCameraBindings(...)` argument to the `esphomeEntities(...)` call in `entity catalog exposes settings and status controls`, built from `MutableStateFlow`s / no-op lambdas exactly like the neighboring bindings, e.g.:

```kotlin
                camera =
                    EsphomeCameraBindings(
                        cameraEnabled = MutableStateFlow(false),
                        setCameraEnabled = {},
                        motionDetected = MutableStateFlow(false),
                        motionMode = MutableStateFlow("off"),
                        setMotionMode = {},
                        motionSensitivity = MutableStateFlow(50f),
                        setMotionSensitivity = {},
                        motionClearDelaySec = MutableStateFlow(15f),
                        setMotionClearDelaySec = {},
                        wakeOnMotion = MutableStateFlow(false),
                        setWakeOnMotion = {},
                        jpegFrames = MutableStateFlow(ByteArray(0)),
                        latestJpeg = { null },
                    ),
```

2. Add the seven new objectIds to the expected-entity assertions (the test asserts the catalog contents around lines 95–136 — read the assertion style there and extend it): `camera_enabled` (switch), `wake_on_motion` (switch), `motion` (binary sensor), `motion_detection_mode` (select), `motion_sensitivity` (number), `motion_clear_delay_sec` (number), `camera` (camera).
3. Any other `esphomeEntities(`/`EsphomeBindings(` call sites in tests get the same camera argument (search: `grep -rn "esphomeEntities(\|EsphomeBindings(" packages/esphome-server/src/test`).

Run: `./gradlew :packages:esphome-server:testDebugUnitTest --tests "com.superdash.esphome.EsphomeBindingsTest"`
Expected: FAIL — `esphomeEntities` has no `camera` parameter.

- [ ] **Step 3: Implement catalog additions**

In `EsphomeBindings.kt`:

1. Add near the other option lists at the top:

```kotlin
private val motionModeOptions = listOf("off", "motion", "person")
```

2. Give `binarySensorEntity` a `deviceClass` parameter:

```kotlin
internal fun binarySensorEntity(
    objectId: String,
    name: String,
    state: Flow<Boolean>,
    deviceClass: String = "",
): EsphomeEntity.BinarySensor =
    EsphomeEntity.BinarySensor(
        key = keyFromObjectId(objectId),
        objectId = objectId,
        name = name,
        state = state,
        deviceClass = deviceClass,
    )
```

3. Add `camera: EsphomeCameraBindings` parameter to `esphomeEntities(...)` (after `doorbell`), and append to the returned list:

```kotlin
        switchEntity(
            objectId = "camera_enabled",
            name = "Camera Enabled",
            state = camera.cameraEnabled,
            onCommand = camera.setCameraEnabled,
        ),
        switchEntity(
            objectId = "wake_on_motion",
            name = "Wake On Motion",
            state = camera.wakeOnMotion,
            onCommand = camera.setWakeOnMotion,
        ),
        binarySensorEntity(
            objectId = "motion",
            name = "Motion",
            state = camera.motionDetected,
            deviceClass = "motion",
        ),
        selectEntity(
            objectId = "motion_detection_mode",
            name = "Motion Detection Mode",
            state = camera.motionMode,
            options = motionModeOptions,
            onCommand = camera.setMotionMode,
        ),
        numberEntity(
            objectId = "motion_sensitivity",
            name = "Motion Sensitivity",
            state = camera.motionSensitivity,
            minValue = 0f,
            maxValue = 100f,
            step = 5f,
            unitOfMeasurement = "%",
            onCommand = camera.setMotionSensitivity,
        ),
        numberEntity(
            objectId = "motion_clear_delay_sec",
            name = "Motion Clear Delay",
            state = camera.motionClearDelaySec,
            minValue = 0f,
            maxValue = 120f,
            step = 5f,
            unitOfMeasurement = "s",
            onCommand = camera.setMotionClearDelaySec,
        ),
        EsphomeEntity.Camera(
            key = keyFromObjectId("camera"),
            objectId = "camera",
            name = "Camera",
            frames = camera.jpegFrames,
            latestJpeg = camera.latestJpeg,
        ),
```

4. Add `camera: EsphomeCameraBindings` to the `EsphomeBindings` class constructor (after `doorbell`) and pass it through in the `entities = { esphomeEntities(...) }` lambda.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :packages:esphome-server:testDebugUnitTest`
Expected: PASS (all esphome-server tests, including the updated catalog assertions).

- [ ] **Step 5: Commit**

```bash
./gradlew ktlintFormat
git add packages/esphome-server
git commit -m "feat(esphome): expose camera, motion sensor, and motion tuning entities"
```

---

### Task 9: App wiring — settings impl, graph, foreground service, wake-on-motion

**Files:**
- Create: `packages/app/src/main/kotlin/com/superdash/settings/SettingsRepositoryCameraSettings.kt`
- Create: `packages/app/src/main/kotlin/com/superdash/camera/CameraService.kt`
- Modify: `packages/app/build.gradle.kts` (add `implementation(project(":packages:camera"))`)
- Modify: `packages/app/src/main/AndroidManifest.xml`
- Modify: `packages/app/src/main/kotlin/com/superdash/AppGraph.kt`
- Modify: `packages/app/src/main/kotlin/com/superdash/EsphomeSubgraph.kt`
- Modify: `packages/app/src/main/kotlin/com/superdash/MainViewModel.kt` + `MainActivity.kt` (service start/stop)
- Test: `packages/app/src/test/kotlin/com/superdash/settings/SettingsRepositoryCameraSettingsTest.kt` (only if sibling settings impls have tests — check `packages/app/src/test`; otherwise rely on the coerce-range pattern being identical to `SettingsRepositoryDoorbellSettings` and skip)

**Interfaces:**
- Consumes: `CameraSettings`, `CameraController`, `CameraXPipeline`, `FrameDiffMotionDetector`, `PersonMotionDetector` (Tasks 1–5); `EsphomeCameraBindings` (Task 8); `KioskEvent.UserTouched`, `KioskEventBus` (existing).
- Produces: `AppGraph.cameraSettings: CameraSettings`, `AppGraph.cameraController: CameraController`, `CameraService` (FGS type `camera`).

- [ ] **Step 1: Settings implementation**

Add to `packages/app/build.gradle.kts` dependencies: `implementation(project(":packages:camera"))`.

Create `packages/app/src/main/kotlin/com/superdash/settings/SettingsRepositoryCameraSettings.kt` (mirror of `SettingsRepositoryDoorbellSettings`):

```kotlin
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
```

(If `Setting`'s actual signature differs, match whatever `SettingsRepositoryDoorbellSettings` uses — it is the canonical example.)

- [ ] **Step 2: Graph wiring**

In `AppGraph.kt`, after `doorbellSettings`:

```kotlin
    val cameraSettings: CameraSettings = SettingsRepositoryCameraSettings(keyValueStore)
```

After `eventBus` (needs `scope`, `eventBus`, `application`):

```kotlin
    private val cameraSensitivity: StateFlow<Int> =
        cameraSettings.motionSensitivity.stateIn(scope, SharingStarted.Eagerly, 50)

    val cameraController: CameraController =
        CameraController(
            pipeline = CameraXPipeline(application.applicationContext),
            settings = cameraSettings,
            detectorFactories =
                mapOf(
                    "motion" to { FrameDiffMotionDetector(sensitivityPercent = { cameraSensitivity.value }) },
                    "person" to { PersonMotionDetector() },
                ),
            scope = scope,
        )
```

And an `init` block (or extend an existing one) for wake-on-motion:

```kotlin
    init {
        scope.launch {
            cameraController.motionActive
                .filter { it }
                .collect {
                    if (cameraSettings.wakeOnMotion.first()) {
                        eventBus.emit(KioskEvent.UserTouched)
                    }
                }
        }
    }
```

Imports to add: `com.superdash.camera.CameraController`, `com.superdash.camera.CameraSettings`, `com.superdash.camera.CameraXPipeline`, `com.superdash.camera.FrameDiffMotionDetector`, `com.superdash.camera.PersonMotionDetector`, `com.superdash.settings.SettingsRepositoryCameraSettings` (same package — not needed), `kotlinx.coroutines.flow.SharingStarted`, `kotlinx.coroutines.flow.filter`, `kotlinx.coroutines.flow.first`, `kotlinx.coroutines.flow.stateIn`, `kotlinx.coroutines.launch`.

NOTE: the wake-on-motion `init` block must appear AFTER the `cameraController` and `eventBus` property declarations (Kotlin initializes in declaration order) — place it at the bottom of the constructor body area, or move the collector into `EsphomeSubgraph`-style helper if ordering gets awkward.

In `EsphomeSubgraph.kt`:
1. Add constructor params `cameraSettings: CameraSettings` and `cameraController: CameraController` (imports `com.superdash.camera.CameraSettings`, `com.superdash.camera.CameraController`, `com.superdash.esphome.EsphomeCameraBindings`).
2. Add before `bindings`:

```kotlin
    private val cameraBindings: EsphomeCameraBindings =
        EsphomeCameraBindings(
            cameraEnabled = cameraSettings.enabled,
            setCameraEnabled = { value -> cameraSettings.setEnabled(value) },
            motionDetected = cameraController.motionActive,
            motionMode = cameraController.activeMotionMode,
            setMotionMode = { value -> cameraSettings.setMotionMode(value) },
            motionSensitivity =
                cameraSettings.motionSensitivity
                    .map { value -> value.toFloat() }
                    .distinctUntilChanged(),
            setMotionSensitivity = { value -> cameraSettings.setMotionSensitivity(value.toInt()) },
            motionClearDelaySec =
                cameraSettings.motionClearDelaySec
                    .map { value -> value.toFloat() }
                    .distinctUntilChanged(),
            setMotionClearDelaySec = { value -> cameraSettings.setMotionClearDelaySec(value.toInt()) },
            wakeOnMotion = cameraSettings.wakeOnMotion,
            setWakeOnMotion = { value -> cameraSettings.setWakeOnMotion(value) },
            jpegFrames = cameraController.jpegFrames,
            latestJpeg = { cameraController.latestJpeg() },
        )
```

3. Pass `camera = cameraBindings` to the `EsphomeBindings(...)` construction.
4. In `AppGraph.kt`, pass `cameraSettings = cameraSettings, cameraController = cameraController` to the `EsphomeSubgraph(...)` construction.

- [ ] **Step 3: Manifest + foreground service**

In `packages/app/src/main/AndroidManifest.xml`, add with the other permissions:

```xml
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
    <uses-feature android:name="android.hardware.camera.any" android:required="false" />
```

And with the other services:

```xml
        <service
            android:name=".camera.CameraService"
            android:foregroundServiceType="camera"
            android:exported="false" />
```

Create `packages/app/src/main/kotlin/com/superdash/camera/CameraService.kt` (mirror `VoiceService`'s shape — notification channel, typed startForeground, stop when disabled):

```kotlin
package com.superdash.camera

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.superdash.R
import com.superdash.SuperdashApp
import com.superdash.core.log.Log
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val CHANNEL_ID = "superdash_camera"
private const val NOTIFICATION_ID = 0xCA3

private val log = Log("CameraService")

/** Foreground service holding the `camera` FGS type while the camera feature
 *  is enabled, so capture keeps working when the kiosk activity is not
 *  resumed (screensaver, doorbell overlay). The pipeline itself lives in
 *  AppGraph's CameraController; this service only anchors the FGS type. */
class CameraService : LifecycleService() {
    override fun onCreate() {
        super.onCreate()
        log.i("onCreate")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            log.w("camera permission missing; stopping camera service")
            stopSelf()
            return
        }
        ensureChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
        )
        val graph = (application as SuperdashApp).graph
        lifecycleScope.launch {
            graph.cameraSettings.enabled.distinctUntilChanged().collect { enabled ->
                if (!enabled) {
                    log.i("camera disabled; stopping camera service")
                    stopSelf()
                }
            }
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.camera_service_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.camera_service_notification_title))
            .setOngoing(true)
            .build()

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, CameraService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraService::class.java))
        }
    }
}
```

Check `VoiceService.kt` for the actual small-icon resource name and notification helpers and reuse them (if `R.drawable.ic_notification` doesn't exist, use whatever `VoiceService.buildNotification()` uses). Add the two strings to `packages/app/src/main/res/values/strings.xml`:

```xml
    <string name="camera_service_channel_name">Camera</string>
    <string name="camera_service_notification_title">Camera active</string>
```

- [ ] **Step 4: Start/stop the service like VoiceService**

In `MainViewModel.kt`, next to `voiceServiceShouldRun` (line ~127), add the analogous flow:

```kotlin
    val cameraServiceShouldRun: StateFlow<Boolean> =
        graph.cameraSettings.enabled
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)
```

(match the exact `stateIn`/scope style used by `voiceServiceShouldRun`).

In `MainActivity.kt`, mirror the voice wiring at lines ~144 and ~277:

```kotlin
    val shouldRunCameraService by viewModel.cameraServiceShouldRun.collectAsStateWithLifecycle()
    LaunchedEffect(shouldRunCameraService) {
        if (shouldRunCameraService) {
            CameraService.start(this@MainActivity)
        } else {
            CameraService.stop(this@MainActivity)
        }
    }
```

(adapt to the exact callback structure used for `VoiceService.start/stop` — same file, same pattern; import `com.superdash.camera.CameraService`).

- [ ] **Step 5: Build and test**

Run: `./gradlew :packages:app:assembleDebug :packages:app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL; existing app tests pass. If `MainViewModel`/`MainActivity` have unit tests referencing constructor shapes you changed, fix them by mirroring the voice equivalents.

- [ ] **Step 6: Commit**

```bash
./gradlew ktlintFormat
git add packages/app gradle settings.gradle.kts
git commit -m "feat(app): wire camera feature into graph, esphome bindings, and camera foreground service"
```

---

### Task 10: Settings UI + camera permission flow

**Files:**
- Create: `packages/app/src/main/kotlin/com/superdash/settings/ui/CameraSettingsSection.kt`
- Modify: `packages/app/src/main/kotlin/com/superdash/settings/SettingsUiState.kt`
- Modify: `packages/app/src/main/kotlin/com/superdash/settings/SettingsViewModel.kt`
- Modify: `packages/app/src/main/kotlin/com/superdash/settings/SettingsContent.kt`
- Modify: `packages/app/src/main/kotlin/com/superdash/settings/SettingsActivity.kt`
- Modify: `packages/app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `CameraSettings` flows via `SettingsViewModel`; existing row composables `SettingsSwitchRow`, `SettingsChoiceRow`, `SettingsChoice` (see `VoiceSettingsSection.kt` for usage).
- Produces: `CameraSettingsState`, `CameraSettingsActions`, `CameraSettingsSection(state, actions)`; `SettingsActivity.requestCameraAndEnable()`.

- [ ] **Step 1: State + actions**

In `SettingsUiState.kt`, following the `VoiceSettingsState` pattern (data class + `empty()` + a field on the root state):

```kotlin
data class CameraSettingsState(
    val enabled: Boolean,
    val facing: String,
    val resolution: String,
    val motionMode: String,
    val motionSensitivity: Int,
    val motionClearDelaySec: Int,
    val wakeOnMotion: Boolean,
) {
    companion object {
        fun empty(): CameraSettingsState =
            CameraSettingsState(
                enabled = false,
                facing = "front",
                resolution = "1280x720",
                motionMode = "motion",
                motionSensitivity = 50,
                motionClearDelaySec = 15,
                wakeOnMotion = false,
            )
    }
}
```

Add `val camera: CameraSettingsState` to the root ui-state data class and `camera = CameraSettingsState.empty()` to its `empty()`. Add next to the other actions classes (e.g. near `SidebarSettingsActions` in `SettingsActivity.kt` or wherever `VoiceSettingsActions` lives — match location):

```kotlin
@Immutable
data class CameraSettingsActions(
    val onRequestCameraEnable: () -> Unit,
    val onCameraDisable: () -> Unit,
    val onFacingChange: (String) -> Unit,
    val onResolutionChange: (String) -> Unit,
    val onMotionModeChange: (String) -> Unit,
    val onMotionSensitivityChange: (Int) -> Unit,
    val onMotionClearDelayChange: (Int) -> Unit,
    val onWakeOnMotionChange: (Boolean) -> Unit,
)
```

- [ ] **Step 2: ViewModel**

In `SettingsViewModel.kt`, mirror how voice settings flows are combined into the ui state (find where `VoiceSettingsState` is built and add a parallel `CameraSettingsState` combine from `graph.cameraSettings.enabled/facing/resolution/motionMode/motionSensitivity/motionClearDelaySec/wakeOnMotion`). Add setters:

```kotlin
    fun setCameraEnabled(value: Boolean) {
        viewModelScope.launch { graph.cameraSettings.setEnabled(value) }
    }

    fun setCameraFacing(value: String) {
        viewModelScope.launch { graph.cameraSettings.setFacing(value) }
    }

    fun setCameraResolution(value: String) {
        viewModelScope.launch { graph.cameraSettings.setResolution(value) }
    }

    fun setCameraMotionMode(value: String) {
        viewModelScope.launch { graph.cameraSettings.setMotionMode(value) }
    }

    fun setCameraMotionSensitivity(value: Int) {
        viewModelScope.launch { graph.cameraSettings.setMotionSensitivity(value) }
    }

    fun setCameraMotionClearDelay(value: Int) {
        viewModelScope.launch { graph.cameraSettings.setMotionClearDelaySec(value) }
    }

    fun setCameraWakeOnMotion(value: Boolean) {
        viewModelScope.launch { graph.cameraSettings.setWakeOnMotion(value) }
    }
```

(match the exact setter style already used for voice — if setters there don't wrap in `viewModelScope.launch`, copy that style instead).

- [ ] **Step 3: Permission flow in `SettingsActivity`**

Mirror the mic pattern (`SettingsActivity.kt:162-177`):

```kotlin
    private val cameraPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            settingsViewModel.setCameraEnabled(granted)
        }

    fun requestCameraAndEnable() {
        val granted =
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            settingsViewModel.setCameraEnabled(true)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
```

Wire `CameraSettingsActions(onRequestCameraEnable = ::requestCameraAndEnable, onCameraDisable = { settingsViewModel.setCameraEnabled(false) }, ...)` wherever the other actions objects are constructed and passed into the content composable.

- [ ] **Step 4: Section composable**

Create `packages/app/src/main/kotlin/com/superdash/settings/ui/CameraSettingsSection.kt` (same idioms as `VoiceSettingsSection.kt` — `ListItem` + `Switch` for the gated enable, `SettingsChoiceRow` for selects):

```kotlin
package com.superdash.settings.ui

import androidx.compose.material3.ListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.superdash.R
import com.superdash.settings.CameraSettingsActions
import com.superdash.settings.CameraSettingsState
import com.superdash.settings.SettingsChoice
import com.superdash.settings.SettingsChoiceRow

@Composable
fun CameraSettingsSection(
    state: CameraSettingsState,
    actions: CameraSettingsActions,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_camera_enabled_title)) },
        supportingContent = { Text(stringResource(R.string.settings_camera_enabled_summary)) },
        trailingContent = {
            Switch(
                checked = state.enabled,
                onCheckedChange = { wanted ->
                    if (wanted) {
                        actions.onRequestCameraEnable()
                    } else {
                        actions.onCameraDisable()
                    }
                },
            )
        },
    )
    if (state.enabled) {
        SettingsChoiceRow(
            label = stringResource(R.string.settings_camera_facing_label),
            choices =
                listOf(
                    SettingsChoice("front", stringResource(R.string.settings_camera_facing_front)),
                    SettingsChoice("back", stringResource(R.string.settings_camera_facing_back)),
                ),
            selectedValue = state.facing,
            fallback = state.facing,
            onSelect = actions.onFacingChange,
        )
        SettingsChoiceRow(
            label = stringResource(R.string.settings_camera_resolution_label),
            choices =
                listOf(
                    SettingsChoice("640x480", "640×480"),
                    SettingsChoice("1280x720", "1280×720"),
                    SettingsChoice("1920x1080", "1920×1080"),
                ),
            selectedValue = state.resolution,
            fallback = state.resolution,
            onSelect = actions.onResolutionChange,
        )
        SettingsChoiceRow(
            label = stringResource(R.string.settings_camera_motion_mode_label),
            choices =
                listOf(
                    SettingsChoice("off", stringResource(R.string.settings_camera_motion_mode_off)),
                    SettingsChoice("motion", stringResource(R.string.settings_camera_motion_mode_motion)),
                    SettingsChoice("person", stringResource(R.string.settings_camera_motion_mode_person)),
                ),
            selectedValue = state.motionMode,
            fallback = state.motionMode,
            onSelect = actions.onMotionModeChange,
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_camera_sensitivity_label)) },
            supportingContent = {
                Slider(
                    value = state.motionSensitivity.toFloat(),
                    onValueChange = { actions.onMotionSensitivityChange(it.toInt()) },
                    valueRange = 0f..100f,
                )
            },
        )
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_camera_wake_on_motion_title)) },
            supportingContent = { Text(stringResource(R.string.settings_camera_wake_on_motion_summary)) },
            trailingContent = {
                Switch(
                    checked = state.wakeOnMotion,
                    onCheckedChange = actions.onWakeOnMotionChange,
                )
            },
        )
    }
}
```

Add to `strings.xml`:

```xml
    <string name="settings_camera_section_title">Camera</string>
    <string name="settings_camera_enabled_title">Camera</string>
    <string name="settings_camera_enabled_summary">Expose the tablet camera and motion sensor to Home Assistant</string>
    <string name="settings_camera_facing_label">Camera</string>
    <string name="settings_camera_facing_front">Front</string>
    <string name="settings_camera_facing_back">Back</string>
    <string name="settings_camera_resolution_label">Resolution</string>
    <string name="settings_camera_motion_mode_label">Motion detection</string>
    <string name="settings_camera_motion_mode_off">Off</string>
    <string name="settings_camera_motion_mode_motion">Any motion</string>
    <string name="settings_camera_motion_mode_person">Person only</string>
    <string name="settings_camera_sensitivity_label">Motion sensitivity</string>
    <string name="settings_camera_wake_on_motion_title">Wake screen on motion</string>
    <string name="settings_camera_wake_on_motion_summary">Exit the screensaver when motion is detected</string>
```

In `SettingsContent.kt`, insert the section after the doorbell section (~line 400), matching how sections are titled and delimited there (there is a `SettingsSectionTitle` helper — follow the neighboring sections):

```kotlin
            SettingsSectionTitle(stringResource(R.string.settings_camera_section_title))
            CameraSettingsSection(
                state = uiState.camera,
                actions = cameraActions,
            )
```

with `cameraActions` threaded the same way voice/doorbell actions reach the composable.

- [ ] **Step 5: Build and test**

Run: `./gradlew :packages:app:assembleDebug :packages:app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, tests pass.

- [ ] **Step 6: Commit**

```bash
./gradlew ktlintFormat
git add packages/app
git commit -m "feat(app): add camera settings section with permission-gated enable"
```

---

### Task 11: Docs, full test run, and on-device verification

**Files:**
- Modify: `packages/README.md` (camera module entry)
- Modify: `AGENTS.md` (package table row)
- Modify: `README.md` (feature bullet)
- Modify: `docs/architecture/ha-integration.md` (mention new entities; follow existing tone)

- [ ] **Step 1: Docs**

Add to `packages/README.md` (after the doorbell entry, same format):

```markdown
## `packages/camera`

Tablet camera and motion detection.

Owns:

- CameraX capture pipeline (NV21 frames, JPEG encoding).
- Frame-diff and ML Kit person motion detectors.
- Motion gate (clear-delay hold).
- Camera controller (settings-driven orchestration).

Start with:

- `src/main/kotlin/com/superdash/camera/CameraController.kt`
- `src/main/kotlin/com/superdash/camera/CameraXPipeline.kt`

Tests:

```bash
./gradlew :packages:camera:testDebugUnitTest
```
```

Add to the AGENTS.md package table: `| \`packages/camera\` | Tablet camera pipeline and motion detection for the ESPHome camera entity. |`

Add a README.md feature bullet: `- Exposes the tablet camera and a motion sensor to Home Assistant.`

In `docs/architecture/ha-integration.md`, add a short paragraph listing the new ESPHome entities (camera, motion binary sensor, camera_enabled/wake_on_motion switches, motion mode select, sensitivity/clear-delay numbers) and the chunked-image constraint (15 KiB chunks under the 16 KiB noise frame cap, ~5 s stream window refreshed by HA).

- [ ] **Step 2: Full verification**

```bash
./gradlew ktlintCheck testDebugUnitTest :packages:app:assembleDebug
```

Expected: all green.

- [ ] **Step 3: On-device smoke test** (see `docs/agent-on-device-testing.md` for device workflow)

1. Install the debug APK on the tablet; open Settings → Camera; toggle on; grant the camera permission.
2. In Home Assistant, the superdash ESPHome device should now show: Camera picture entity, Motion binary sensor, Camera Enabled / Wake On Motion switches, Motion Detection Mode select, Motion Sensitivity / Motion Clear Delay numbers.
3. Open the camera picture card — live view should update at several fps.
4. Wave a hand in front of the tablet — Motion turns on in HA and clears after the configured delay.
5. Set mode to `person`, verify motion only fires with a visible face; set to `off`, verify no motion events.
6. Enable Wake Screen On Motion, start the screensaver, walk up to the tablet — the screensaver exits.
7. Toggle Camera Enabled off from HA — availability drops, stream stops, no frames are captured (camera indicator disappears).

- [ ] **Step 4: Commit**

```bash
./gradlew ktlintFormat
git add packages/README.md AGENTS.md README.md docs/architecture
git commit -m "docs: document camera module and esphome camera entities"
```
