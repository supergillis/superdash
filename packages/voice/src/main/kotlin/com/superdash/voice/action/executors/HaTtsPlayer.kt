package com.superdash.voice.action.executors

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.superdash.core.log.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val log = Log("HaTtsPlayer")

/** Tiny seam so [VoicePipelineCoordinator] is unit-testable without instantiating
 *  the real ExoPlayer-backed [HaTtsPlayer]. Production wires the real player. */
interface TtsPlay {
    suspend fun play(url: String)

    fun stop()
}

/** Media3 ExoPlayer wrapper.
 *
 *  USAGE_MEDIA + handleAudioFocus = true → ducks any music playing in
 *  another app while TTS speaks, then restores focus on completion.
 *  (Media3 only allows automatic audio focus with USAGE_MEDIA / USAGE_GAME;
 *  the spec's nominal USAGE_ASSISTANT choice throws at construction time.)
 *  AUDIO_CONTENT_TYPE_SPEECH still hints "this is voice" to the routing layer.
 *  Bearer-auth header is added to the HTTP data source so HA's TTS proxy
 *  endpoint accepts the request.
 */
class HaTtsPlayer(
    private val context: Context,
    private val tokenProvider: suspend () -> String = { "" },
    private val haBaseUrlProvider: () -> String? = { null },
) : TtsPlay,
    AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val player: ExoPlayer =
        ExoPlayer
            .Builder(context)
            .setAudioAttributes(
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .build(),
                // handleAudioFocus =
                true,
            ).build()

    override suspend fun play(url: String) {
        val bearerToken = tokenProvider()
        val resolvedUrl = resolveHaMediaUrl(url, haBaseUrlProvider())
        val httpFactory =
            DefaultHttpDataSource
                .Factory()
                .setDefaultRequestProperties(mapOf("Authorization" to "Bearer $bearerToken"))
        val sourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory)
        val source = sourceFactory.createMediaSource(MediaItem.fromUri(resolvedUrl))
        val completion = CompletableDeferred<Unit>()
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        completion.complete(Unit)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    log.w("playback error", error)
                    completion.complete(Unit)
                }
            }
        try {
            withContext(Dispatchers.Main) {
                player.addListener(listener)
                player.setMediaSource(source)
                player.prepare()
                player.play()
            }
            completion.await()
        } finally {
            withContext(Dispatchers.Main) {
                player.removeListener(listener)
                player.stop()
                player.clearMediaItems()
            }
        }
    }

    override fun stop() {
        mainHandler.post {
            player.stop()
        }
    }

    override fun close() {
        // ExoPlayer must be released on the application thread it was built on
        // (Media3 enforces this via IllegalStateException). stop() already routes
        // through mainHandler; close() must do the same.
        mainHandler.post {
            player.release()
        }
    }
}
