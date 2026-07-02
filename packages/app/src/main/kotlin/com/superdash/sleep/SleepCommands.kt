package com.superdash.sleep

/** Commands targeted at the kiosk's sleep/night-mode state.
 *
 *  Direct interface (not a bus event) because there is exactly one consumer
 *  ([SleepController]) and there is no fan-out value in routing through
 *  [com.superdash.kiosk.bus.KioskEventBus]. */
interface SleepCommands {
    suspend fun setNightModeActive(value: Boolean)
}
