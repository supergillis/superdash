package com.superdash.kiosk.bus

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Application-wide event bus for transient kiosk signals.
 *
 *  - replay=0: late collectors do not see "the user touched 5 minutes ago".
 *  - buffer=64: tolerates short bursts (touch storms, rapid wake-word fires).
 *  - DROP_OLDEST: a stalled consumer never blocks producers. */
class KioskEventBus {
    private val mutableEvents =
        MutableSharedFlow<KioskEvent>(
            replay = 0,
            extraBufferCapacity = 64,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val events: SharedFlow<KioskEvent> = mutableEvents.asSharedFlow()

    fun emit(event: KioskEvent) {
        mutableEvents.tryEmit(event)
    }
}
