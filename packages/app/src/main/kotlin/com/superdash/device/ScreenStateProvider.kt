package com.superdash.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import com.superdash.core.log.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val log = Log("ScreenStateProvider")

/** Tracks display interactivity via PowerManager + ACTION_SCREEN_ON/OFF
 *  broadcasts. Exposed as a StateFlow consumed by ESPHome's `screen_on`
 *  binary_sensor. */
class ScreenStateProvider(
    private val context: Context,
) {
    private val powerManager: PowerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private val mutableState = MutableStateFlow(powerManager.isInteractive)
    val state: StateFlow<Boolean> = mutableState.asStateFlow()
    private var registered = false

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context?,
                intent: Intent?,
            ) {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_ON -> {
                        mutableState.value = true
                        log.i("screen on")
                    }
                    Intent.ACTION_SCREEN_OFF -> {
                        mutableState.value = false
                        log.i("screen off")
                    }
                }
            }
        }

    fun start() {
        if (registered) {
            mutableState.value = powerManager.isInteractive
            return
        }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        context.registerReceiver(receiver, filter)
        registered = true
        mutableState.value = powerManager.isInteractive
    }

    fun stop() {
        if (!registered) {
            return
        }
        context.unregisterReceiver(receiver)
        registered = false
    }
}
