package com.superdash.feed

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a feed's trigger entity maps to overlay visibility.
 *
 *  [Momentary] is edge shaped: the overlay opens on a transition and closes on
 *  its own. [Sustained] is level shaped: the overlay is open for as long as the
 *  entity reports one of [Sustained.activeStates]. */
@Serializable
sealed interface FeedTrigger {
    @Serializable
    @SerialName("momentary")
    data object Momentary : FeedTrigger

    @Serializable
    @SerialName("sustained")
    data class Sustained(
        val activeStates: List<String> = listOf("on"),
    ) : FeedTrigger
}

const val FEED_ACTIVE_STATE_WILDCARD = "*"

private val UNREACHABLE_STATES = setOf("unavailable", "unknown", "")

/** Level predicate. Takes only the current state: a sustained feed whose trigger
 *  is already active at app start must show without waiting for a transition. */
fun FeedTrigger.Sustained.isActive(state: String?): Boolean {
    if (state == null) {
        return false
    }
    if (activeStates.contains(FEED_ACTIVE_STATE_WILDCARD)) {
        return state !in UNREACHABLE_STATES
    }
    return state in activeStates
}
