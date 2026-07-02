package com.superdash.screensaver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import com.superdash.ha.HaMediaSourceClient
import com.superdash.ha.HaWebSocketClient
import com.superdash.immich.ImmichApiClient
import com.superdash.screensaver.overlay.OverlayPosition
import com.superdash.screensaver.overlay.freeCornersFor
import com.superdash.screensaver.overlay.pickRandomCorner
import com.superdash.screensaver.slideshow.HaMediaLibrarySource
import com.superdash.screensaver.slideshow.ImmichCatalogStore
import com.superdash.screensaver.slideshow.ImmichSlideshowSource
import com.superdash.screensaver.slideshow.PicsumSlideshowSource
import com.superdash.screensaver.slideshow.SlideshowScreensaver
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.datetime.Clock

private const val RANDOM_ROTATION_MS = 15L * 60 * 1000

/** UI state for [ScreensaverHostContent]. */
@Immutable
data class ScreensaverHostUiState(
    val weather: WeatherSnapshot?,
    val calendar: CalendarSnapshot?,
    val energy: EnergySnapshot?,
    /** Resolved corner. Never Random. */
    val ambientCorner: OverlayPosition,
    val showAmbient: Boolean,
)

/** Stateful host: collects screensaver-related state from settings + HA,
 *  resolves Random into a rotating concrete corner, picks the active
 *  Screensaver instance, and delegates rendering to [ScreensaverHostContent]. */
@Composable
fun ScreensaverHost(
    settings: ScreensaverSettings,
    immichAlbumFlow: Flow<String>,
    immichCatalogTtlHoursFlow: Flow<Int>,
    haClient: HaWebSocketClient,
    haMediaSource: HaMediaSourceClient,
    immichClient: StateFlow<ImmichApiClient?>,
    immichCatalogStore: ImmichCatalogStore,
    onImmichSourceCreated: (ImmichSlideshowSource) -> Unit = {},
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    val dayKey by settings.dayMode.collectAsStateWithLifecycle(initialValue = "photos")
    val nightKey by settings.nightMode.collectAsStateWithLifecycle(initialValue = "black")
    val nightActive by settings.nightModeActive.collectAsStateWithLifecycle(initialValue = false)
    val mode = ScreensaverMode.fromKey(if (nightActive) nightKey else dayKey)
    val weatherEntityId by settings.weatherEntityId.collectAsStateWithLifecycle(initialValue = "weather.home")
    val positionKey by settings.overlayPosition.collectAsStateWithLifecycle(initialValue = "bottom_left")
    val configuredPosition = OverlayPosition.fromKey(positionKey)
    val pictureSpacingDp by settings.pictureSpacingDp.collectAsStateWithLifecycle(initialValue = 8)
    val sourceId by settings.mediaLibrarySourceId.collectAsStateWithLifecycle(initialValue = null)
    val orderKey by settings.mediaLibraryOrder.collectAsStateWithLifecycle(initialValue = "shuffle")
    val immichAlbum by immichAlbumFlow.collectAsStateWithLifecycle(initialValue = "")
    val immichCatalogTtlHours by immichCatalogTtlHoursFlow.collectAsStateWithLifecycle(initialValue = 24)
    val entity by haClient.observeEntity(weatherEntityId).collectAsStateWithLifecycle(initialValue = null)
    val weather = WeatherSnapshot.fromEntity(entity)
    val calendarEntityId by settings.calendarEntityId.collectAsStateWithLifecycle(initialValue = "")
    val calendarEntity by haClient
        .observeEntity(calendarEntityId)
        .collectAsStateWithLifecycle(initialValue = null)
    val calendar = CalendarSnapshot.fromEntity(calendarEntity)
    val powerUsageEntityId by settings.powerUsageEntityId.collectAsStateWithLifecycle(initialValue = "")
    val solarPowerEntityId by settings.solarPowerEntityId.collectAsStateWithLifecycle(initialValue = "")
    val gridPowerEntityId by settings.gridPowerEntityId.collectAsStateWithLifecycle(initialValue = "")
    val powerUsageEntity by haClient
        .observeEntity(powerUsageEntityId)
        .collectAsStateWithLifecycle(initialValue = null)
    val solarPowerEntity by haClient
        .observeEntity(solarPowerEntityId)
        .collectAsStateWithLifecycle(initialValue = null)
    val gridPowerEntity by haClient
        .observeEntity(gridPowerEntityId)
        .collectAsStateWithLifecycle(initialValue = null)
    val energy = EnergySnapshot.fromEntities(powerUsageEntity, solarPowerEntity, gridPowerEntity)

    var resolvedAmbientCorner by remember {
        mutableStateOf(
            if (configuredPosition == OverlayPosition.Random) {
                pickRandomCorner()
            } else {
                configuredPosition
            },
        )
    }
    LaunchedEffect(configuredPosition) {
        if (configuredPosition == OverlayPosition.Random) {
            while (true) {
                resolvedAmbientCorner = pickRandomCorner()
                delay(RANDOM_ROTATION_MS)
            }
        } else {
            resolvedAmbientCorner = configuredPosition
        }
    }

    val screensaver: Screensaver? =
        when (mode) {
            ScreensaverMode.Photos -> {
                val source = remember { PicsumSlideshowSource() }
                remember(source, imageLoader, pictureSpacingDp) {
                    SlideshowScreensaver(source, imageLoader, pictureSpacingDp = pictureSpacingDp)
                }
            }
            ScreensaverMode.MediaLibrary -> {
                val nonNullId = sourceId
                if (nonNullId.isNullOrEmpty()) {
                    remember { ClockScreensaver() }
                } else {
                    val source =
                        remember(nonNullId, orderKey) {
                            HaMediaLibrarySource(haMediaSource, nonNullId, orderKey)
                        }
                    remember(source, imageLoader, pictureSpacingDp) {
                        SlideshowScreensaver(source, imageLoader, pictureSpacingDp = pictureSpacingDp)
                    }
                }
            }
            ScreensaverMode.Clock -> remember { ClockScreensaver() }
            ScreensaverMode.Immich -> {
                val client by immichClient.collectAsStateWithLifecycle()
                val nonNullClient = client
                if (nonNullClient == null) {
                    remember { ClockScreensaver() }
                } else {
                    val latestTtlMs by rememberUpdatedState(immichCatalogTtlHours * 3_600_000L)
                    val ttlMsSupplier = remember<() -> Long> { { latestTtlMs } }
                    val source =
                        remember(nonNullClient, immichAlbum) {
                            ImmichSlideshowSource(
                                client = nonNullClient,
                                albumName = immichAlbum,
                                catalogStore = immichCatalogStore,
                                catalogTtlMs = ttlMsSupplier,
                            )
                        }
                    // Registration is a side effect, not a value computation, so it belongs in
                    // LaunchedEffect, not `remember`. Compose may invoke the `remember` block
                    // again on configuration changes; doing the registration here guarantees
                    // it fires exactly once per source instance.
                    LaunchedEffect(source) { onImmichSourceCreated(source) }
                    DisposableEffect(source) {
                        onDispose { source.close() }
                    }
                    remember(source, imageLoader, pictureSpacingDp) {
                        SlideshowScreensaver(source, imageLoader, pictureSpacingDp = pictureSpacingDp)
                    }
                }
            }
            ScreensaverMode.Black -> remember { BlackScreensaver() }
            ScreensaverMode.Off -> null
        }

    val state =
        ScreensaverHostUiState(
            weather = weather,
            calendar = calendar,
            energy = energy,
            ambientCorner = resolvedAmbientCorner,
            showAmbient = mode != ScreensaverMode.Off,
        )

    ScreensaverHostContent(
        state = state,
        screensaver = screensaver,
        modifier = modifier,
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ScreensaverHostContentPreview() {
    ScreensaverHostContent(
        state =
            ScreensaverHostUiState(
                weather =
                    WeatherSnapshot(
                        state = "sunny",
                        temperatureC = 21.0,
                        unit = "C",
                        humidity = 45.0,
                        forecast = persistentListOf(),
                    ),
                calendar =
                    CalendarSnapshot(
                        message = "Dentist appointment",
                        startTime = Clock.System.now(),
                        endTime = null,
                        allDay = false,
                        inProgress = false,
                    ),
                energy =
                    EnergySnapshot(
                        usageW = 1450.0,
                        solarW = 3200.0,
                        gridW = -1750.0,
                    ),
                ambientCorner = OverlayPosition.BottomRight,
                showAmbient = true,
            ),
        screensaver =
            object : Screensaver {
                override val id: String = "preview"

                @Composable
                override fun Content(modifier: Modifier) {
                    Box(modifier.fillMaxSize().background(Color.DarkGray))
                }
            },
        modifier = Modifier.fillMaxSize(),
    )
}

/** Stateless renderer over UiState + Screensaver instance.
 *  Z-order: content, screensaver overlays, then ambient overlay. */
@Composable
fun ScreensaverHostContent(
    state: ScreensaverHostUiState,
    screensaver: Screensaver?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        screensaver?.Content(Modifier.fillMaxSize())

        val overlays = screensaver?.Overlays() ?: emptyList()
        val freeCorners = if (overlays.isEmpty()) emptyList() else freeCornersFor(state.ambientCorner)
        overlays.zip(freeCorners).forEach { (overlay, corner) ->
            overlay(corner)
        }

        if (state.showAmbient) {
            AmbientOverlay(
                weather = state.weather,
                calendar = state.calendar,
                energy = state.energy,
                position = state.ambientCorner,
            )
        }
    }
}
