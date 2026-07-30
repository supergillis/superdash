package com.superdash.kiosk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.superdash.feed.FeedOverlay
import com.superdash.feed.FeedState
import com.superdash.feed.FeedStreamState
import com.superdash.feed.resolveFeedStream

@Immutable
data class KioskOverlayState(
    val feedState: FeedState,
    val feedAutoCloseSec: Int,
    val haBaseUrl: String,
    val isIdle: Boolean,
)

/** Feed-over-screensaver overlay stack shared by MainContent and
 *  SettingsActivity. Caller provides the screensaver content as a slot.
 *
 *  Owns feed stream resolution (HLS URL + bearer token) so
 *  `FeedOverlay` stays a dumb body. Resolution gates on
 *  [shouldStartFeedStream] which factors in activity-foreground:
 *  while the activity is paused, no `camera/stream` round-trip fires. */
@Composable
fun KioskOverlays(
    state: KioskOverlayState,
    bearerTokenProvider: suspend () -> String?,
    fetchHlsUrl: suspend (cameraEntity: String) -> String,
    onCloseFeed: () -> Unit,
    onTapScreensaver: () -> Unit,
    screensaverContent: @Composable () -> Unit,
) {
    val activityForeground = rememberActivityForegroundState().value
    AnimatedVisibility(
        visible = state.isIdle,
        enter = fadeIn(tween(500)),
        exit = fadeOut(tween(250)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTapScreensaver,
                    ),
        ) {
            screensaverContent()
        }
    }
    val showing = state.feedState as? FeedState.Showing
    if (showing != null) {
        val streamActive =
            shouldStartFeedStream(
                feedState = state.feedState,
                activityForeground = activityForeground,
            )
        var streamState: FeedStreamState by
            remember(showing.config.id) { mutableStateOf(FeedStreamState.Resolving) }
        LaunchedEffect(showing.config.id, streamActive) {
            if (!streamActive) {
                streamState = FeedStreamState.Resolving
                return@LaunchedEffect
            }
            streamState = FeedStreamState.Resolving
            streamState =
                resolveFeedStream(
                    config = showing.config,
                    haBaseUrl = state.haBaseUrl,
                    fetchHlsUrl = fetchHlsUrl,
                    bearerTokenProvider = bearerTokenProvider,
                )
        }
        FeedOverlay(
            state = showing,
            streamState = streamState,
            autoCloseSec = state.feedAutoCloseSec,
            onClose = onCloseFeed,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
