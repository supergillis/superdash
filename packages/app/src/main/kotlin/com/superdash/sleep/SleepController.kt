package com.superdash.sleep

import com.superdash.core.log.Log
import com.superdash.kiosk.bus.KioskEvent
import com.superdash.kiosk.bus.KioskEventBus
import com.superdash.screensaver.Touchable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private val log = Log("SleepController")

/** Owns the kiosk's "is the user supposed to be asleep right now?" state.
 *
 *  - Exposes [nightModeActive] derived from internal settings.
 *  - Collects [KioskEventBus] wake events and forwards them to
 *    [ScreensaverIdleController.touch], so producers (touch, wake-word,
 *    doorbell) don't need to know about the idle controller.
 *  - Implements [SleepCommands] for direct setters (no bus indirection
 *    because there is no fan-out). */
class SleepController(
    nightModeActiveFlow: Flow<Boolean>,
    private val setNightModeActive: suspend (Boolean) -> Unit,
    bus: KioskEventBus,
    idleController: Touchable,
    scope: CoroutineScope,
) : SleepCommands {
    val nightModeActive: StateFlow<Boolean> =
        nightModeActiveFlow
            .stateIn(scope, SharingStarted.Eagerly, false)

    init {
        scope.launch {
            bus.events.collect { event ->
                when (event) {
                    is KioskEvent.UserTouched,
                    is KioskEvent.WakeWordDetected,
                    is KioskEvent.DoorbellRingStarted,
                    -> {
                        log.i("wake event → idle touch", "event" to event::class.simpleName)
                        idleController.touch()
                    }
                }
            }
        }
    }

    override suspend fun setNightModeActive(value: Boolean) {
        log.i("night mode request", "active" to value)
        setNightModeActive.invoke(value)
    }
}
