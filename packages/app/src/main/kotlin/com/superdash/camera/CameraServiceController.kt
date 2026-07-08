package com.superdash.camera

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/** Drives [CameraService]'s lifecycle from the camera-enabled setting on a
 *  process-scoped coroutine, independent of any Activity. This is why the
 *  camera-type foreground service starts as soon as the camera is enabled
 *  (from Settings, over ESPHome, anywhere) rather than only when the kiosk
 *  activity is resumed — without it, a screen-off/doze revokes camera access
 *  and the stream freezes.
 *
 *  [start] is gated on the screen being on: the FGS can only be started while
 *  the device is awake (Android forbids background foreground-service starts),
 *  and the screen-on rising edge re-attempts a start that was missed while
 *  asleep (e.g. remote enable over ESPHome on a sleeping tablet), so the camera
 *  comes up on the next wake. [stop] fires on disable regardless of screen
 *  state. [start]/[stop] are injected so this stays free of Android APIs and
 *  unit-testable; AppGraph wires them to CameraService.start/stop and
 *  [screenOn] to screenStateProvider.state. */
class CameraServiceController(
    enabled: Flow<Boolean>,
    screenOn: Flow<Boolean>,
    scope: CoroutineScope,
    private val start: () -> Unit,
    private val stop: () -> Unit,
) {
    init {
        scope.launch {
            enabled.distinctUntilChanged().collect { isEnabled ->
                if (!isEnabled) stop()
            }
        }
        scope.launch {
            combine(enabled, screenOn) { isEnabled, isScreenOn -> isEnabled && isScreenOn }
                .distinctUntilChanged()
                .collect { active -> if (active) start() }
        }
    }
}
