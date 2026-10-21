package com.superdash.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import com.superdash.core.log.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException

private val log = Log("AeadDataStoreSerializer")

private const val KEY_RETRY_INITIAL_DELAY_MS = 1_000L
private const val KEY_RETRY_MAX_DELAY_MS = 60_000L

// Caps the shift so the doubling cannot overflow before the delay cap applies.
private const val KEY_RETRY_MAX_SHIFT = 16L

/** DataStore serializer that keeps [T] encrypted at rest. A null value is an empty file.
 *  Corrupt data reads as null, while [KeyUnavailableException] propagates so the
 *  stored value is never mistaken for missing. */
class AeadDataStoreSerializer<T : Any>(
    private val encryption: AeadEncryption,
    private val encode: (T) -> ByteArray,
    private val decode: (ByteArray) -> T,
) : Serializer<T?> {
    override val defaultValue: T? = null

    override suspend fun readFrom(input: InputStream): T? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) {
            return null
        }
        val plaintext =
            try {
                encryption.decrypt(bytes)
            } catch (e: GeneralSecurityException) {
                log.w("stored ciphertext is corrupt; treating as empty", e)
                return null
            } catch (e: RuntimeException) {
                // A bad payload can also surface unchecked from Tink.
                log.w("stored ciphertext is corrupt; treating as empty", e)
                return null
            }
        return try {
            decode(plaintext)
        } catch (e: IllegalArgumentException) {
            // Also covers kotlinx SerializationException, which extends it.
            log.w("stored value does not decode; treating as empty", e)
            null
        }
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override suspend fun writeTo(value: T?, output: OutputStream) {
        if (value == null) {
            return
        }
        val ciphertext =
            try {
                encryption.encrypt(encode(value))
            } catch (e: GeneralSecurityException) {
                throw IOException("Failed to encrypt value", e)
            }
        output.write(ciphertext)
    }
}

/** True when [KeyUnavailableException] is anywhere in the cause chain. DataStore wraps
 *  read failures in its own IOException, so checking the top type is not enough. */
fun Throwable.isKeyUnavailable(): Boolean = generateSequence(this) { it.cause }.any { it is KeyUnavailableException }

/** Retries the upstream read while the key is unavailable, so collectors only
 *  ever see a value. The stack trace is logged once per outage. */
fun <T> Flow<T>.retryWhileKeyUnavailable(storeName: String): Flow<T> =
    retryWhen { cause, attempt ->
        if (!cause.isKeyUnavailable()) {
            return@retryWhen false
        }
        val shift = attempt.coerceAtMost(KEY_RETRY_MAX_SHIFT).toInt()
        val delayMs = (KEY_RETRY_INITIAL_DELAY_MS shl shift).coerceAtMost(KEY_RETRY_MAX_DELAY_MS)
        log.w(
            "encryption key unavailable; retrying",
            cause.takeIf { attempt == 0L },
            "store" to storeName,
            "attempt" to attempt,
            "delayMs" to delayMs,
        )
        delay(delayMs)
        true
    }

/** Writes null, or deletes [file] when the key is unavailable. updateData reads the
 *  current value first, which fails during an outage, yet clearing must still work. */
suspend fun <T : Any> DataStore<T?>.clearOrDelete(file: File, storeName: String) {
    try {
        updateData { null }
    } catch (e: IOException) {
        if (!e.isKeyUnavailable()) {
            throw e
        }
        log.w("store unreadable; deleting it to clear", e, "store" to storeName)
        val deleted = withContext(Dispatchers.IO) { file.delete() || !file.exists() }
        if (!deleted) {
            throw e
        }
    }
}
