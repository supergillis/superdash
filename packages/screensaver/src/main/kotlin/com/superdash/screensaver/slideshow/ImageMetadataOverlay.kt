package com.superdash.screensaver.slideshow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.superdash.screensaver.overlay.OverlayPosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val whiteFaded = Color.White.copy(alpha = 0.75f)

private val withShadow =
    TextStyle(
        shadow =
            Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 1.5f),
                blurRadius = 4f,
            ),
    )

/** Per-image metadata caption rendered at [position]. */
@Composable
fun ImageMetadataOverlay(
    item: SlideshowItem,
    position: OverlayPosition,
    modifier: Modifier = Modifier,
) {
    val media = item.media.firstOrNull() ?: return
    ImageMetadataOverlay(media = media, position = position, modifier = modifier)
}

@Composable
fun ImageMetadataOverlay(
    media: SlideshowMedia,
    position: OverlayPosition,
    modifier: Modifier = Modifier,
) {
    val caption = metadataCaption(media) ?: return
    Box(modifier.fillMaxSize()) {
        Text(
            caption,
            fontSize = 14.sp,
            color = whiteFaded,
            style = withShadow,
            modifier =
                Modifier
                    .align(position.toAlignment())
                    .padding(32.dp),
        )
    }
}

internal fun metadataCaption(
    media: SlideshowMedia,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault(),
): String? {
    val parts =
        listOfNotNull(
            media.locationLabel?.takeIf { it.isNotBlank() },
            media.date?.let { formatDateTime(it, locale, timeZone) },
        )
    if (parts.isEmpty()) {
        return null
    }
    return parts.joinToString("  ")
}

private fun OverlayPosition.toAlignment(): Alignment =
    when (this) {
        OverlayPosition.TopLeft -> Alignment.TopStart
        OverlayPosition.TopRight -> Alignment.TopEnd
        OverlayPosition.BottomLeft -> Alignment.BottomStart
        OverlayPosition.BottomRight -> Alignment.BottomEnd
        OverlayPosition.Random -> Alignment.BottomEnd // defensive: host should resolve
    }

private fun formatDateTime(
    instant: Instant,
    locale: Locale,
    timeZone: TimeZone,
): String =
    SimpleDateFormat("MMM d, yyyy HH:mm", locale)
        .apply { this.timeZone = timeZone }
        .format(Date(instant.toEpochMilli()))

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ImageMetadataOverlayPreview() {
    ImageMetadataOverlay(
        media =
            SlideshowMedia(
                url = "https://example.test/photo.jpg",
                date = Instant.ofEpochMilli(0),
                locationLabel = "Springfield, USA",
            ),
        position = OverlayPosition.BottomLeft,
    )
}
