package com.superdash.core.util

/** Marker for enums whose values map to/from a stable `key` string used in
 *  persisted settings or wire formats. The string is the source of truth.
 *  The enum is just a typed view of it.
 *
 *  Example:
 *
 *  ```
 *  enum class ScreensaverMode(override val key: String) : KeyedEnum {
 *      Photos("photos"),
 *      Black("black"),
 *  }
 *
 *  val mode = keyOf<ScreensaverMode>(savedKey, default = ScreensaverMode.Photos)
 *  ```
 */
interface KeyedEnum {
    val key: String
}

inline fun <reified E> keyOf(key: String, default: E): E
where E : Enum<E>, E : KeyedEnum =
    enumValues<E>().firstOrNull { it.key == key } ?: default
