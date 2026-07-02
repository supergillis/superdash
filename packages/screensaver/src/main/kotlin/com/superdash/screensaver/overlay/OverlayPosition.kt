package com.superdash.screensaver.overlay

import com.superdash.core.util.KeyedEnum
import com.superdash.core.util.keyOf

enum class OverlayPosition(
    override val key: String,
) : KeyedEnum {
    TopLeft("top_left"),
    TopRight("top_right"),
    BottomLeft("bottom_left"),
    BottomRight("bottom_right"),
    Random("random"),
    ;

    companion object {
        fun fromKey(key: String): OverlayPosition = keyOf(key, default = BottomLeft)
    }
}

/** Diagonally-opposite corner. Random maps to itself
 *  (both layers then rotate together). */
fun OverlayPosition.opposite(): OverlayPosition =
    when (this) {
        OverlayPosition.TopLeft -> OverlayPosition.BottomRight
        OverlayPosition.TopRight -> OverlayPosition.BottomLeft
        OverlayPosition.BottomLeft -> OverlayPosition.TopRight
        OverlayPosition.BottomRight -> OverlayPosition.TopLeft
        OverlayPosition.Random -> OverlayPosition.Random
    }
