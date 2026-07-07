package com.superdash.screensaver.overlay

/** Free-corner priority for a given ambient corner. Order: diagonal first
 *  (so a single screensaver overlay lands diagonally across from ambient,
 *  preserving Nest-Hub-style layering), then the two adjacent corners. */
fun freeCornersFor(ambient: OverlayPosition): List<OverlayPosition> =
    when (ambient) {
        OverlayPosition.BottomLeft ->
            listOf(
                OverlayPosition.TopRight,
                OverlayPosition.TopLeft,
                OverlayPosition.BottomRight,
            )
        OverlayPosition.BottomRight ->
            listOf(
                OverlayPosition.TopLeft,
                OverlayPosition.TopRight,
                OverlayPosition.BottomLeft,
            )
        OverlayPosition.TopLeft ->
            listOf(
                OverlayPosition.BottomRight,
                OverlayPosition.BottomLeft,
                OverlayPosition.TopRight,
            )
        OverlayPosition.TopRight ->
            listOf(
                OverlayPosition.BottomLeft,
                OverlayPosition.BottomRight,
                OverlayPosition.TopLeft,
            )
        OverlayPosition.Random -> error("ambientCorner must be resolved (not Random) before assignment")
    }

fun pickRandomCorner(): OverlayPosition =
    listOf(
        OverlayPosition.TopLeft,
        OverlayPosition.TopRight,
        OverlayPosition.BottomLeft,
        OverlayPosition.BottomRight,
    ).random()
