package com.superdash.esphome.glue

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import com.superdash.core.security.AeadDataStoreSerializer
import com.superdash.core.security.AeadEncryption
import com.superdash.core.security.clearOrDelete
import com.superdash.core.security.retryWhileKeyUnavailable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Encrypted at-rest store for the ESPHome native-API Noise PSK.
 *
 *  The PSK is a 32-byte binary value; we store it raw inside the AEAD payload
 *  rather than as base64 to keep the on-disk size bounded. */
class EsphomePskStore(
    context: Context,
    encryption: AeadEncryption,
    fileName: String = "esphome_psk.bin",
) {
    private val file = context.applicationContext.dataStoreFile(fileName)

    private val store: DataStore<ByteArray?> =
        DataStoreFactory.create(
            serializer = AeadDataStoreSerializer(encryption, encode = { psk -> psk }, decode = { bytes -> bytes }),
            produceFile = { file },
        )

    // A null PSK turns encryption off, so an unavailable key retries instead of reading as null.
    val psk: Flow<ByteArray?> =
        store.data
            .retryWhileKeyUnavailable("EsphomePskStore")
            .map { it?.takeIf { bytes -> bytes.size == 32 } }

    suspend fun set(psk: ByteArray) {
        require(psk.size == 32) { "PSK must be 32 bytes" }
        store.updateData { psk }
    }

    suspend fun clear() {
        store.clearOrDelete(file, "EsphomePskStore")
    }
}
