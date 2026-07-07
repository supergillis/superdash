package com.superdash.immich

import kotlinx.coroutines.flow.Flow

/**
 * Typed settings view owned by the immich feature.
 *
 * Lives in the feature package so the feature never imports the persistence
 * layer. The `app` module provides the implementation.
 */
interface ImmichSettings {
    val url: Flow<String>

    val apiKey: Flow<String>

    val album: Flow<String>

    val catalogTtlHours: Flow<Int>

    suspend fun setUrl(value: String)

    suspend fun setApiKey(value: String)

    suspend fun setAlbum(value: String)

    suspend fun setCatalogTtlHours(value: Int)
}
