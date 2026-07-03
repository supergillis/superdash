package com.superdash.kiosk.ui

sealed interface AppState {
    /** Initial state before persisted settings (HA URL, tokens) have loaded.
     *  Shown as a neutral loading screen so an already-configured user does not
     *  briefly see the first-run setup form on launch. */
    data object Loading : AppState

    data object NeedsSetup : AppState

    data class NeedsReauth(
        val haUrl: String,
        val reason: String,
    ) : AppState

    data class Configured(
        val haUrl: String,
    ) : AppState
}
