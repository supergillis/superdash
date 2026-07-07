package com.superdash.settings

import kotlinx.coroutines.flow.Flow

interface PskStore {
    val psk: Flow<ByteArray?>

    suspend fun set(psk: ByteArray)

    suspend fun clear()
}
