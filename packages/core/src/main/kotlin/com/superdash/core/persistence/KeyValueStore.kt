package com.superdash.core.persistence

import kotlinx.coroutines.flow.Flow

/**
 * Typed key/value port for primitive settings.
 *
 * Each call to [flow] returns the current value for [key] or [default] if absent.
 * Implementations must support Boolean, Int, Long, Float, Double, and String.
 */
interface KeyValueStore {
    fun <T : Any> flow(key: String, default: T): Flow<T>

    suspend fun <T : Any> set(key: String, value: T)

    suspend fun <T : Any> mutate(
        key: String,
        default: T,
        transform: (T) -> T,
    ): T
}
