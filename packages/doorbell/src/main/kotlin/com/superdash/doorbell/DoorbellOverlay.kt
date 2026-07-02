package com.superdash.doorbell

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.superdash.core.log.Log
import kotlinx.coroutines.delay

private val log = Log("DoorbellOverlay")

/** Full-screen doorbell view: live feed + close button + (disabled) PTT.
 *
 *  Dumb body. Consumes a pre-resolved [DoorbellStreamState]; URL/token
 *  resolution lives in the caller (see `KioskOverlays`). Renders by
 *  branching on [streamState] and an internal `firstFrameRendered`
 *  signal from ExoPlayer. */
@Composable
fun DoorbellOverlay(
    state: DoorbellState.Showing,
    streamState: DoorbellStreamState,
    autoCloseSec: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playbackError: String? by remember(state.config.id, streamState) { mutableStateOf(null) }
    var firstFrameRendered: Boolean by remember(state.config.id, streamState) { mutableStateOf(false) }

    if (autoCloseSec > 0) {
        // Key on autoCloseSec too so changing it via Settings while the overlay is
        // open restarts the timer with the new duration instead of finishing the
        // stale delay.
        LaunchedEffect(state.openedAtEpochMs, autoCloseSec) {
            delay(autoCloseSec * 1000L)
            onClose()
        }
    }

    BackHandler(onBack = onClose)

    val resolveFailedMessage = (streamState as? DoorbellStreamState.Failed)?.reason
    val errorMessage = playbackError ?: resolveFailedMessage

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        when {
            errorMessage != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(72.dp),
                    )
                    Text(
                        errorMessage,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            streamState is DoorbellStreamState.Ready -> {
                StreamPlayer(
                    streamUrl = streamState.streamUrl,
                    bearerToken = streamState.bearerToken,
                    onFirstFrame = { firstFrameRendered = true },
                    onError = { message -> playbackError = message },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        // Loading state: cover all paths before the first frame paints, except
        // the explicit error state above. Shows immediately when the overlay
        // opens and stays visible during HLS fetch + ExoPlayer buffering.
        if (errorMessage == null && !firstFrameRendered) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(color = Color.White)
                Text(
                    "Connecting…",
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        IconButton(
            onClick = onClose,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(56.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
        FilledIconButton(
            // PTT wiring lands in a later milestone.
            onClick = { },
            enabled = false,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(64.dp),
            colors = IconButtonDefaults.filledIconButtonColors(),
        ) {
            Icon(Icons.Default.Mic, contentDescription = "Push to talk (coming soon)")
        }
    }
}

@Composable
private fun StreamPlayer(
    streamUrl: String,
    bearerToken: String?,
    onFirstFrame: () -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Build the player once per (url, token) pair so a token refresh while the
    // overlay is open swaps to a fresh player instead of mutating a live one.
    val player =
        remember(streamUrl, bearerToken) {
            val httpFactory =
                DefaultHttpDataSource
                    .Factory()
                    .apply {
                        if (bearerToken != null) {
                            setDefaultRequestProperties(mapOf("Authorization" to "Bearer $bearerToken"))
                        }
                    }
            val sourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory)
            // Reolink (and similar) cameras advertise H.264 High@5.1 in their SDP
            // even when the actual frames are 1080p or smaller. Many tablet HW
            // decoders reject Level 5.1 outright (NO_EXCEEDS_CAPABILITIES). Enabling
            // decoder fallback lets ExoPlayer try a software H.264 decoder when no
            // hardware codec accepts the level; software at 1080p is fine on CPU.
            val renderersFactory =
                DefaultRenderersFactory(context).setEnableDecoderFallback(true)
            ExoPlayer
                .Builder(context, renderersFactory)
                .setMediaSourceFactory(sourceFactory)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(streamUrl))
                    prepare()
                    playWhenReady = true
                }
        }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        onFirstFrame()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    log.w("playback error", error, "code" to error.errorCodeName)
                    onError(error.message ?: "Stream interrupted")
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            PlayerView(ctx).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                useController = false
                this.player = player
            }
        },
        update = { view ->
            view.player = player
        },
    )
}
