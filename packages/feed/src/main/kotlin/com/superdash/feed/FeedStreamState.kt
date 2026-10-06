package com.superdash.feed

/** Resolution status of a feed live stream.
 *
 *  Produced by [resolveFeedStream] and consumed by `FeedOverlay`.
 *  Splits "what to play" from "how it renders" so the overlay stays a dumb
 *  body. */
sealed interface FeedStreamState {
    data object Idle : FeedStreamState

    data object Resolving : FeedStreamState

    data class Ready(
        val streamUrl: String,
        val bearerToken: String?,
    ) : FeedStreamState

    data class Failed(
        val reason: String?,
    ) : FeedStreamState
}
