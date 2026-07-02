package com.superdash.ha

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.superdash.core.log.Log
import com.superdash.ha.security.AeadEncryption
import com.superdash.ha.security.KeystoreKeyProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private val log = Log("HaTokenStore")

class HaTokenStore(
    context: Context,
) : HaTokenStoreLike {
    private val encryption = AeadEncryption(KeystoreKeyProvider(context.applicationContext))

    private val store: DataStore<HaTokens?> =
        DataStoreFactory.create(
            serializer = AeadHaTokensSerializer(encryption),
            produceFile = { context.applicationContext.dataStoreFile("ha_secrets.bin") },
        )

    val tokensFlow: Flow<HaTokens?> = store.data

    override suspend fun load(): HaTokens? = store.data.first()

    suspend fun loadAccessToken(): String? = load()?.accessToken

    override suspend fun save(tokens: HaTokens) {
        store.updateData { tokens }
    }

    override suspend fun clear() {
        store.updateData { null }
    }
}

private class AeadHaTokensSerializer(
    private val encryption: AeadEncryption,
) : Serializer<HaTokens?> {
    override val defaultValue: HaTokens? = null

    override suspend fun readFrom(input: InputStream): HaTokens? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) {
            return null
        }
        return try {
            val plaintext = encryption.decrypt(bytes)
            haJson.decodeFromString<HaTokens>(plaintext.decodeToString())
        } catch (t: Throwable) {
            log.w("failed to decrypt token store; treating as empty", t)
            null
        }
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun writeTo(tokens: HaTokens?, output: OutputStream) {
        if (tokens == null) {
            // Persist an empty file by writing zero bytes.
            return
        }
        val plaintext = haJson.encodeToString(HaTokens.serializer(), tokens).encodeToByteArray()
        val ciphertext =
            try {
                encryption.encrypt(plaintext)
            } catch (e: Throwable) {
                throw IOException("Failed to encrypt HaTokens", e)
            }
        output.write(ciphertext)
    }
}
