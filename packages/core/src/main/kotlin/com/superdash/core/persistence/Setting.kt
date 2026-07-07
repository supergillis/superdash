package com.superdash.core.persistence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Declarative descriptor for a single typed key/value entry.
 *
 * [read] transforms the raw stored value before it leaves the store and
 * [write] sanitises a value before it lands. Validators like `coerceIn`,
 * enum normalisation, or `trim()` belong in these hooks so call sites stay
 * one-liners.
 */
class Setting<T : Any>(
    val key: String,
    val default: T,
    val read: (T) -> T = { it },
    val write: (T) -> T = { it },
)

/** Observe [setting] as a [Flow], applying [Setting.read] on every emission.
 *  `distinctUntilChanged` is required: DataStore re-emits the full Preferences
 *  snapshot on any key write, so without it every observer would re-emit on
 *  every unrelated settings change. */
fun <T : Any> KeyValueStore.observe(setting: Setting<T>): Flow<T> =
    flow(setting.key, setting.default).map(setting.read).distinctUntilChanged()

/** Persist [value] for [setting] after applying [Setting.write]. */
suspend fun <T : Any> KeyValueStore.write(
    setting: Setting<T>,
    value: T,
) {
    set(setting.key, setting.write(value))
}

suspend fun <T : Any> KeyValueStore.mutate(
    setting: Setting<T>,
    transform: (T) -> T,
): T =
    mutate(setting.key, setting.default) { current ->
        setting.write(transform(setting.read(current)))
    }.let(setting.read)
