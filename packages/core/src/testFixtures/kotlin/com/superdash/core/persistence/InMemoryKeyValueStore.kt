package com.superdash.core.persistence

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [KeyValueStore] for tests. Tracks values per key and emits updates
 * through a per-key [MutableStateFlow] so `flow(key, default).first()` reflects
 * the latest `set(key, value)` write.
 */
class InMemoryKeyValueStore(
    private val state: MutableMap<String, Any?> = mutableMapOf(),
) : KeyValueStore {
    private val flows = mutableMapOf<String, MutableStateFlow<Any?>>()
    private val mutex = Mutex()

    override fun <T : Any> flow(
        key: String,
        default: T,
    ): Flow<T> {
        val current = flows.getOrPut(key) { MutableStateFlow(state[key] ?: default) }
        return current.map {
            @Suppress("UNCHECKED_CAST")
            (it ?: default) as T
        }
    }

    override suspend fun <T : Any> set(
        key: String,
        value: T,
    ) = mutex.withLock {
        state[key] = value
        flows.getOrPut(key) { MutableStateFlow(value) }.value = value
    }

    override suspend fun <T : Any> mutate(
        key: String,
        default: T,
        transform: (T) -> T,
    ): T =
        mutex.withLock {
            @Suppress("UNCHECKED_CAST")
            val current = (state[key] ?: default) as T
            val updated = transform(current)
            state[key] = updated
            flows.getOrPut(key) { MutableStateFlow(updated) }.value = updated
            updated
        }
}
