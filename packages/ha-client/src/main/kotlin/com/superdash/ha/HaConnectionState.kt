package com.superdash.ha

/** UI-bound HA WebSocket connection state. */
sealed interface HaConnectionState {
    data object Disconnected : HaConnectionState {
        override fun toString() = "Disconnected"
    }

    data object Connecting : HaConnectionState {
        override fun toString() = "Connecting"
    }

    data class Connected(
        val haVersion: String,
    ) : HaConnectionState

    data class Failed(
        val reason: String,
    ) : HaConnectionState

    data class NeedsReauth(
        val reason: String,
    ) : HaConnectionState
}
