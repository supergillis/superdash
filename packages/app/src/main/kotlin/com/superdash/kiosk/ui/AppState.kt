package com.superdash.kiosk.ui

sealed interface AppState {
    data object NeedsSetup : AppState

    data class NeedsReauth(
        val haUrl: String,
        val reason: String,
    ) : AppState

    data class Configured(
        val haUrl: String,
    ) : AppState
}
