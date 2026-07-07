package com.superdash.esphome.glue

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.datastore.dataStoreFile
import com.superdash.core.log.Log
import com.superdash.ha.security.AeadEncryption
import com.superdash.ha.security.KeystoreKeyProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

private val log = Log("EsphomePskStore")

/** Encrypted at-rest store for the ESPHome native-API Noise PSK.
 *
 *  Uses the same [AeadEncryption] keyset as [com.superdash.ha.HaTokenStore]
 *  (`superdash_token_keyset`), so the master key in Android Keystore is shared.
 *  The PSK is a 32-byte binary value; we store it raw inside the AEAD payload
 *  rather than as base64 to keep the on-disk size bounded. */
class EsphomePskStore(
    context: Context,
    fileName: String = "esphome_psk.bin",
) {
    private val encryption = AeadEncryption(KeystoreKeyProvider(context.applicationContext))

    private val store: DataStore<ByteArray?> =
        DataStoreFactory.create(
            serializer = AeadByteArraySerializer(encryption),
            produceFile = { context.applicationContext.dataStoreFile(fileName) },
        )

    val psk: Flow<ByteArray?> = store.data.map { it?.takeIf { bytes -> bytes.size == 32 } }

    suspend fun set(psk: ByteArray) {
        require(psk.size == 32) { "PSK must be 32 bytes" }
        store.updateData { psk }
    }

    suspend fun clear() {
        store.updateData { null }
    }
}

private class AeadByteArraySerializer(
    private val encryption: AeadEncryption,
) : Serializer<ByteArray?> {
    override val defaultValue: ByteArray? = null

    override suspend fun readFrom(input: InputStream): ByteArray? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) {
            return null
        }
        return try {
            encryption.decrypt(bytes)
        } catch (t: Throwable) {
            log.w("failed to decrypt PSK store; treating as empty", t)
            null
        }
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun writeTo(psk: ByteArray?, output: OutputStream) {
        if (psk == null) {
            return
        }
        val ciphertext =
            try {
                encryption.encrypt(psk)
            } catch (e: Throwable) {
                throw IOException("Failed to encrypt ESPHome PSK", e)
            }
        output.write(ciphertext)
    }
}
