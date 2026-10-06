package com.superdash.feed

sealed interface FeedState {
    data object Idle : FeedState

    data class Showing(
        val config: FeedConfig,
        val openedAtEpochMs: Long,
    ) : FeedState
}
