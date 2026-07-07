package com.superdash.kiosk.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.superdash.SuperdashApp
import com.superdash.core.log.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

private val log = Log("BootReceiver")

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                val app = context.applicationContext as SuperdashApp
                val settings = app.graph.settings
                withTimeout(BOOT_RECEIVER_TIMEOUT_MS) {
                    val snapshot = settings.snapshot()
                    log.i(
                        "onReceive",
                        "action" to intent.action,
                        "startOnBoot" to snapshot.startOnBoot,
                        "launchOnWake" to snapshot.launchOnWake,
                    )
                    BootStartupHandler(
                        loadSnapshot = { snapshot },
                        launch = { BootLauncher.launch(context) },
                    ).handle(intent.action)
                }
            } catch (t: Throwable) {
                log.e("failed", t)
            } finally {
                pendingResult.finish()
                scope.cancel()
            }
        }
    }

    private companion object {
        const val BOOT_RECEIVER_TIMEOUT_MS = 8_000L
    }
}
