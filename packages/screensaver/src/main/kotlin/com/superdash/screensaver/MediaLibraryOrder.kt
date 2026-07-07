package com.superdash.screensaver

import com.superdash.core.util.KeyedEnum
import com.superdash.core.util.keyOf

enum class MediaLibraryOrder(
    override val key: String,
) : KeyedEnum {
    Shuffle("shuffle"),
    Chronological("chronological"),
    ;

    companion object {
        fun fromKey(key: String): MediaLibraryOrder = keyOf(key, default = Shuffle)
    }
}
