package com.superdash.screensaver

import com.superdash.core.util.KeyedEnum
import com.superdash.core.util.keyOf

enum class ScreensaverMode(
    override val key: String,
) : KeyedEnum {
    Off("off"),
    Photos("photos"),
    Immich("immich"),
    MediaLibrary("media_library"),
    Clock("clock"),
    Black("black"),
    ;

    companion object {
        fun fromKey(key: String): ScreensaverMode = keyOf(key, default = Photos)

        fun fromKeyOrNull(key: String): ScreensaverMode? = entries.firstOrNull { it.key == key }
    }
}
