package com.superdash.doorbell

import kotlinx.coroutines.flow.Flow

/**
 * Typed settings view owned by the doorbell feature.
 *
 * The interface lives in the feature package so the feature module never
 * imports the persistence layer. The `app` module provides the implementation.
 */
interface DoorbellSettings {
    val enabled: Flow<Boolean>

    val autoCloseSec: Flow<Int>

    val doorbells: Flow<List<DoorbellConfig>>

    suspend fun setEnabled(value: Boolean)

    suspend fun setAutoCloseSec(value: Int)

    suspend fun upsertDoorbell(config: DoorbellConfig)

    suspend fun removeDoorbell(id: String)
}
