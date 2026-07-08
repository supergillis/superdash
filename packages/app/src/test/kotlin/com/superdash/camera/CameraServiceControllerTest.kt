package com.superdash.camera

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraServiceControllerTest {
    @Test
    fun `starts when enabled while screen is on`() =
        runTest(UnconfinedTestDispatcher()) {
            val enabled = MutableStateFlow(false)
            val screenOn = MutableStateFlow(true)
            var starts = 0
            var stops = 0
            CameraServiceController(enabled, screenOn, backgroundScope, { starts++ }, { stops++ })
            assertEquals(0, starts)

            enabled.value = true
            assertEquals(1, starts)
        }

    @Test
    fun `does not start while screen is off`() =
        runTest(UnconfinedTestDispatcher()) {
            val enabled = MutableStateFlow(false)
            val screenOn = MutableStateFlow(false)
            var starts = 0
            CameraServiceController(enabled, screenOn, backgroundScope, { starts++ }, {})
            enabled.value = true
            assertEquals(0, starts) // enabled but asleep: no background FGS start

            screenOn.value = true // wake
            assertEquals(1, starts) // wake-retry fires the missed start
        }

    @Test
    fun `stops when disabled`() =
        runTest(UnconfinedTestDispatcher()) {
            val enabled = MutableStateFlow(true)
            val screenOn = MutableStateFlow(true)
            var stops = 0
            CameraServiceController(enabled, screenOn, backgroundScope, {}, { stops++ })

            enabled.value = false
            assertEquals(1, stops)
        }

    @Test
    fun `does not re-start on duplicate awake-and-enabled emissions`() =
        runTest(UnconfinedTestDispatcher()) {
            // SharedFlow (not StateFlow) so duplicate emissions actually reach
            // the operator — this proves the distinctUntilChanged, not StateFlow
            // conflation.
            val enabled = MutableSharedFlow<Boolean>(replay = 1)
            val screenOn = MutableSharedFlow<Boolean>(replay = 1)
            var starts = 0
            CameraServiceController(enabled, screenOn, backgroundScope, { starts++ }, {})
            screenOn.emit(true)
            enabled.emit(true)
            enabled.emit(true) // duplicate true
            screenOn.emit(true) // duplicate true
            assertEquals(1, starts)
        }
}
