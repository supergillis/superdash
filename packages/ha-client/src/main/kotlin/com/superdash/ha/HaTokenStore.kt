package com.superdash.ha

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.superdash.core.security.AeadDataStoreSerializer
import com.superdash.core.security.AeadEncryption
import com.superdash.core.security.clearOrDelete
import com.superdash.core.security.retryWhileKeyUnavailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.File

class HaTokenStore internal constructor(
    encryption: AeadEncryption,
    private val file: File,
    scope: CoroutineScope,
) : HaTokenStoreLike {
    constructor(context: Context, encryption: AeadEncryption) : this(
        encryption = encryption,
        file = context.applicationContext.dataStoreFile("ha_secrets.bin"),
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    )

    private val store: DataStore<HaTokens?> =
        DataStoreFactory.create(
            serializer =
                AeadDataStoreSerializer(
                    encryption = encryption,
                    encode = { tokens -> haJson.encodeToString(HaTokens.serializer(), tokens).encodeToByteArray() },
                    decode = { bytes -> haJson.decodeFromString(HaTokens.serializer(), bytes.decodeToString()) },
                ),
            scope = scope,
            produceFile = { file },
        )

    val tokensFlow: Flow<HaTokens?> = store.data.retryWhileKeyUnavailable("HaTokenStore")

    /** Throws [com.superdash.core.security.KeyUnavailableException] while the key cannot be loaded. */
    override suspend fun load(): HaTokens? = store.data.first()

    suspend fun loadAccessToken(): String? = load()?.accessToken

    override suspend fun save(tokens: HaTokens) {
        store.updateData { tokens }
    }

    override suspend fun clear() {
        store.clearOrDelete(file, "HaTokenStore")
    }
}
