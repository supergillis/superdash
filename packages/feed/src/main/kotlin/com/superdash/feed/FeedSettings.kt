package com.superdash.feed

import kotlinx.coroutines.flow.Flow

/**
 * Typed settings view owned by the feed feature.
 *
 * The interface lives in the feature package so the feature module never
 * imports the persistence layer. The `app` module provides the implementation.
 */
interface FeedSettings {
    val enabled: Flow<Boolean>

    val autoCloseSec: Flow<Int>

    val feeds: Flow<List<FeedConfig>>

    suspend fun setEnabled(value: Boolean)

    suspend fun setAutoCloseSec(value: Int)

    suspend fun upsertFeed(config: FeedConfig)

    suspend fun removeFeed(id: String)
}
