package com.superdash.kiosk.bus

import com.superdash.core.log.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

private val log = Log("ActivityCommandQueue")

/** Activity-scoped command channel.
 *
 *  - One consumer per item: each [ActivityCommand] is delivered to exactly one
 *    collector. Use for commands that mutate Activity state.
 *  - Replay-when-attached: commands submitted while no collector is attached
 *    are buffered and delivered to the next collector.
 *  - Bounded buffer (64): producers suspend on overflow.
 *
 *  Contrast with [KioskEventBus], which broadcasts notification facts to all
 *  collectors with `DROP_OLDEST` overflow. */
class ActivityCommandQueue {
    private val channel = Channel<ActivityCommand>(capacity = 64)

    /** Collect to drain pending commands. The Activity attaches by collecting
     *  inside `repeatOnLifecycle(STARTED)`; while detached, the channel buffers. */
    val commands: Flow<ActivityCommand> = channel.receiveAsFlow()

    /** Submit a command. Suspends only when the buffer is full (64 unconsumed). */
    suspend fun submit(command: ActivityCommand) {
        log.i("submit", "command" to command::class.simpleName)
        channel.send(command)
    }
}
