package com.superdash.kiosk.bus

/** Notification facts emitted via [KioskEventBus].
 *
 *  ## What belongs here
 *  Past-tense / noun-event signals that 0..N independent components may care about:
 *  - [UserTouched]: user interacted with the kiosk (touch, key, esphome stop-screensaver).
 *  - [WakeWordDetected]: wake-word fired; carries the detected phrase.
 *  - [DoorbellRingStarted]: a configured doorbell fired; carries the doorbell id
 *    and the ring timestamp. Consumers that need the typed config resolve it from
 *    their own settings flow.
 *
 *  ## What does NOT belong here
 *  - **Activity-targeted commands**: `RefreshWebView`, `RestartApp`. These are
 *    one-consumer commands that must not be dropped when the Activity is paused.
 *    Use [ActivityCommandQueue] instead.
 *  - **No-fan-out indirection**: if exactly one consumer exists and the goal is
 *    to avoid an import, use a direct interface call (see
 *    [com.superdash.sleep.SleepCommands]). The bus is overhead, not decoupling,
 *    when there is no fan-out.
 *  - **Hot flows / large payloads**: a [kotlinx.coroutines.flow.Flow] of audio
 *    buffers, a `DoorbellConfig` object, etc. Pass primitives (entity ids,
 *    timestamps, phrases) and let consumers resolve typed objects from their own
 *    dependencies.
 *
 *  ## Buffer semantics
 *  `replay = 0`, `extraBufferCapacity = 64`, `BufferOverflow.DROP_OLDEST`. Late
 *  subscribers do not see prior emissions; under burst load a stalled consumer
 *  drops events from the back of the queue. Both are correct for fan-out facts:
 *  "user touched 5 minutes ago" is not actionable, and "the user touched 70 times
 *  while you were paused" reduces to "wake up". */
sealed class KioskEvent {
    object UserTouched : KioskEvent()

    data class WakeWordDetected(
        val phrase: String,
    ) : KioskEvent()

    data class DoorbellRingStarted(
        val doorbellId: String,
        val timestampMs: Long,
    ) : KioskEvent()
}
