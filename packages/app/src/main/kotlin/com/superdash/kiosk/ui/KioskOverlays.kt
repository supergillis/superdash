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
import com.superdash.doorbell.DoorbellOverlay
import com.superdash.doorbell.DoorbellState
import com.superdash.doorbell.DoorbellStreamState
import com.superdash.doorbell.resolveDoorbellStream

@Immutable
data class KioskOverlayState(
    val doorbellState: DoorbellState,
    val doorbellAutoCloseSec: Int,
    val haBaseUrl: String,
    val isIdle: Boolean,
)

/** Doorbell-over-screensaver overlay stack shared by MainContent and
 *  SettingsActivity. Caller provides the screensaver content as a slot.
 *
 *  Owns doorbell stream resolution (HLS URL + bearer token) so
 *  `DoorbellOverlay` stays a dumb body. Resolution gates on
 *  [shouldStartDoorbellStream] which factors in activity-foreground:
 *  while the activity is paused, no `camera/stream` round-trip fires. */
@Composable
fun KioskOverlays(
    state: KioskOverlayState,
    bearerTokenProvider: suspend () -> String?,
    fetchHlsUrl: suspend (cameraEntity: String) -> String,
    onCloseDoorbell: () -> Unit,
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
    val showing = state.doorbellState as? DoorbellState.Showing
    if (showing != null) {
        val streamActive =
            shouldStartDoorbellStream(
                doorbellState = state.doorbellState,
                activityForeground = activityForeground,
            )
        var streamState: DoorbellStreamState by
            remember(showing.config.id) { mutableStateOf(DoorbellStreamState.Resolving) }
        LaunchedEffect(showing.config.id, streamActive) {
            if (!streamActive) {
                streamState = DoorbellStreamState.Resolving
                return@LaunchedEffect
            }
            streamState = DoorbellStreamState.Resolving
            streamState =
                resolveDoorbellStream(
                    config = showing.config,
                    haBaseUrl = state.haBaseUrl,
                    fetchHlsUrl = fetchHlsUrl,
                    bearerTokenProvider = bearerTokenProvider,
                )
        }
        DoorbellOverlay(
            state = showing,
            streamState = streamState,
            autoCloseSec = state.doorbellAutoCloseSec,
            onClose = onCloseDoorbell,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
