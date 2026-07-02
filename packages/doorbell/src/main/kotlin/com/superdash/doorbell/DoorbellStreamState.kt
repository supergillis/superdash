package com.superdash.doorbell

/** Resolution status of a doorbell live stream.
 *
 *  Produced by [resolveDoorbellStream] and consumed by `DoorbellOverlay`.
 *  Splits "what to play" from "how it renders" so the overlay stays a dumb
 *  body. */
sealed interface DoorbellStreamState {
    data object Idle : DoorbellStreamState

    data object Resolving : DoorbellStreamState

    data class Ready(
        val streamUrl: String,
        val bearerToken: String?,
    ) : DoorbellStreamState

    data class Failed(
        val reason: String,
    ) : DoorbellStreamState
}
