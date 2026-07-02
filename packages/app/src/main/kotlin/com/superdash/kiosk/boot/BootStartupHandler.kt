package com.superdash.kiosk.boot

import com.superdash.settings.SettingsRepository

class BootStartupHandler(
    private val loadSnapshot: suspend () -> SettingsRepository.Snapshot,
    private val launch: () -> Unit,
) {
    suspend fun handle(action: String?) {
        val snapshot = loadSnapshot()
        if (BootDecision.shouldLaunch(action, snapshot)) {
            launch()
        }
    }
}
