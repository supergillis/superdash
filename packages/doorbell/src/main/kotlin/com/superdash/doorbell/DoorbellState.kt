package com.superdash.doorbell

sealed interface DoorbellState {
    data object Idle : DoorbellState

    data class Showing(
        val config: DoorbellConfig,
        val openedAtEpochMs: Long,
    ) : DoorbellState
}
