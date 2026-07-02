package com.superdash.screensaver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** The subset of [ScreensaverIdleController] that wake-event consumers depend
 *  on. Lets [com.superdash.sleep.SleepController] (and tests) take this without
 *  pulling in the full timer/coroutine machinery. */
fun interface Touchable {
    fun touch()
}

/** Tracks user inactivity and exposes [isIdle] as a StateFlow.
 *
 *  - [touch] is called from Activity-level dispatchTouchEvent / dispatchKeyEvent.
 *  - [pause] / [resume] mirror Activity onPause / onResume.
 *  - When [timeoutSecondsFlow] emits 0, idle is permanently false.
 *
 *  Implementation: a single coroutine collects [timeoutSecondsFlow] with collectLatest;
 *  per timeout value, it polls [clock] at most every 1s while idle, or computes the
 *  exact next-tick delay while not idle. Cheap and correct under virtual time. */
class ScreensaverIdleController(
    private val timeoutSecondsFlow: Flow<Int>,
    scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : Touchable {
    private val mutableIsIdle = MutableStateFlow(false)
    val isIdle: StateFlow<Boolean> = mutableIsIdle.asStateFlow()

    @Volatile private var lastTouchAt = clock()

    @Volatile private var paused = false

    init {
        scope.launch {
            timeoutSecondsFlow.collectLatest { timeoutSec ->
                if (timeoutSec <= 0) {
                    mutableIsIdle.value = false
                    return@collectLatest
                }
                while (true) {
                    val elapsedMs = clock() - lastTouchAt
                    val targetMs = timeoutSec * 1_000L
                    val shouldIdle = !paused && elapsedMs >= targetMs
                    mutableIsIdle.value = shouldIdle
                    val nextTickMs = if (shouldIdle) 1_000L else (targetMs - elapsedMs).coerceAtLeast(100L)
                    delay(nextTickMs)
                }
            }
        }
    }

    override fun touch() {
        lastTouchAt = clock()
        if (mutableIsIdle.value) {
            mutableIsIdle.value = false
        }
    }

    /** Force the screensaver to show right now, regardless of elapsed inactivity.
     *  Backdates [lastTouchAt] so the polling loop also computes "idle". This means
     *  the next [touch] (e.g. a tap on the screensaver itself) cleanly dismisses.
     *
     *  Also clears [paused]: the typical caller is SettingsActivity's "Test"
     *  button, at which point MainActivity is paused (and would otherwise have
     *  the polling loop short-circuit isIdle to false on the next tick). */
    fun forceIdle() {
        paused = false
        lastTouchAt = 0L
        mutableIsIdle.value = true
    }

    fun pause() {
        paused = true
        mutableIsIdle.value = false
    }

    fun resume() {
        paused = false
        lastTouchAt = clock()
    }
}
