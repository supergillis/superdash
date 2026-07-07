package com.superdash.screensaver.slideshow

import android.content.res.Configuration
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.superdash.screensaver.Screensaver
import com.superdash.screensaver.ScreensaverOverlay
import kotlinx.coroutines.flow.collectLatest

/** Generic slideshow screensaver. Plug a [SlideshowSource] in and it cycles
 *  through items on [intervalMs] ticks. Class-level [currentItem] state is
 *  shared between [Content] (renders) and [Overlays] (caption).
 *
 *  Lifetime is bound by the host's `remember(source, imageLoader)`.
 *  changing source or imageLoader produces a new instance, GC'ing the
 *  prior state. */
class SlideshowScreensaver(
    private val source: SlideshowSource,
    private val imageLoader: ImageLoader,
    private val intervalMs: Long = 30_000L,
    private val pictureSpacingDp: () -> Int = { 8 },
    private val historyCapacity: Int = 20,
) : Screensaver {
    override val id: String = "slideshow:${source.id}"

    private var currentItem by mutableStateOf<SlideshowItem?>(null)

    @Composable
    override fun Content(modifier: Modifier) {
        val configuration = LocalConfiguration.current
        val viewport =
            if (configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
                SlideshowViewport.Portrait
            } else {
                SlideshowViewport.Landscape
            }

        val controllerScope = rememberCoroutineScope()
        val controller =
            remember(source) {
                SlideshowLoopController(
                    source = source,
                    intervalMs = intervalMs,
                    historyCapacity = historyCapacity,
                    scope = controllerScope,
                    initialViewport = viewport,
                )
            }

        DisposableEffect(controller) {
            controller.start()
            onDispose { controller.stop() }
        }

        LaunchedEffect(controller, viewport) {
            controller.setViewport(viewport)
        }

        LaunchedEffect(controller) {
            controller.currentItem.collectLatest { item -> currentItem = item }
        }

        val density = LocalDensity.current
        val swipeThresholdPx = remember(density) { with(density) { 64.dp.toPx() } }

        Box(
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(swipeThresholdPx, controller) {
                    var accumulator = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { accumulator = 0f },
                        onDragEnd = {
                            val total = accumulator
                            accumulator = 0f
                            when {
                                total <= -swipeThresholdPx -> controller.requestForward()
                                total >= swipeThresholdPx -> controller.requestBack()
                            }
                        },
                        onDragCancel = { accumulator = 0f },
                    ) { _, dragAmount -> accumulator += dragAmount }
                },
        ) {
            val item = currentItem
            when (item) {
                is SlideshowVideo -> {
                    VideoPane(
                        media = item.video,
                        fillViewport = item.fillViewport,
                        onFinished = { controller.notifyVideoFinished() },
                    )
                }
                is SlideshowImage -> {
                    if (item.media.size == 1) {
                        PhotoPane(
                            media = item.media.first(),
                            imageLoader = imageLoader,
                            contentScale =
                                if (item.fillViewport) {
                                    ContentScale.Crop
                                } else {
                                    ContentScale.Fit
                                },
                        )
                    } else {
                        ImageGroupPane(item = item, imageLoader = imageLoader, pictureSpacingDp = pictureSpacingDp())
                    }
                }
                null -> Unit
            }
        }
    }

    @Composable
    override fun Overlays(): List<ScreensaverOverlay> {
        val item = currentItem ?: return emptyList()
        val captionMedia = slideCaptionMedia(item) ?: return emptyList()
        return listOf(
            ScreensaverOverlay { corner ->
                ImageMetadataOverlay(media = captionMedia, position = corner)
            },
        )
    }
}

internal fun slideCaptionMedia(item: SlideshowItem): SlideshowMedia? =
    item.media.firstOrNull { media -> metadataCaption(media) != null }

@Composable
private fun ImageGroupPane(
    item: SlideshowImage,
    imageLoader: ImageLoader,
    pictureSpacingDp: Int,
) {
    if (item.isStacked) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(pictureSpacingDp.dp),
        ) {
            item.media.forEach { media ->
                PhotoPane(
                    media = media,
                    imageLoader = imageLoader,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(pictureSpacingDp.dp),
        ) {
            item.media.forEach { media ->
                PhotoPane(
                    media = media,
                    imageLoader = imageLoader,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PhotoPane(
    media: SlideshowMedia,
    imageLoader: ImageLoader,
    contentScale: ContentScale = ContentScale.Crop,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalPlatformContext.current
    val request =
        ImageRequest
            .Builder(ctx)
            .data(media.url)
            .crossfade(true)
            .build()
    Box(modifier.fillMaxSize()) {
        AsyncImage(
            model = request,
            imageLoader = imageLoader,
            contentDescription = null,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun VideoPane(
    media: SlideshowMedia,
    fillViewport: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var finished by remember(media.url) { mutableStateOf(false) }

    fun finishOnce() {
        if (!finished) {
            finished = true
            onFinished()
        }
    }

    val player =
        remember(media.url, media.requestHeaders) {
            val dataSourceFactory =
                DefaultHttpDataSource
                    .Factory()
                    .setDefaultRequestProperties(media.requestHeaders)
            val sourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(sourceFactory)
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(media.url))
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_OFF
                    playWhenReady = true
                    prepare()
                }
        }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) {
                        finishOnce()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    finishOnce()
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    useController = false
                    resizeMode =
                        if (fillViewport) {
                            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        } else {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    this.player = player
                }
            },
            update = { view ->
                view.player = player
                view.resizeMode =
                    if (fillViewport) {
                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    } else {
                        AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
            },
        )
    }
}
