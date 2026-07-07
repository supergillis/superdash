package com.superdash.core.persistence

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface FileMutationRunner {
    suspend fun <T> mutate(block: suspend () -> T): T
}

class SerializedFileMutationRunner(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FileMutationRunner {
    private val mutex = Mutex()

    override suspend fun <T> mutate(block: suspend () -> T): T =
        mutex.withLock {
            withContext(dispatcher) {
                block()
            }
        }
}
