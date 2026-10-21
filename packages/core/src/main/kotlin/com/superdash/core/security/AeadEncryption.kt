package com.superdash.core.security

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.superdash.core.log.Log
import java.io.IOException

private val log = Log("AeadEncryption")

// Renaming any of these orphans every value encrypted with the current keyset.
private const val KEYSET_NAME = "superdash_token_keyset"
private const val MASTER_KEY_URI = "android-keystore://superdash_master_key"
private const val KEYSET_PREF_NAME = "superdash_token_keyset_pref"

/** Both calls throw [KeyUnavailableException] while the key cannot be loaded.
 *  A [java.security.GeneralSecurityException] from [decrypt] means the
 *  ciphertext itself is bad. */
interface AeadEncryption {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray = byteArrayOf()): ByteArray

    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray = byteArrayOf()): ByteArray
}

/** The key could not be loaded, so the stored data is intact but unreadable for now. */
class KeyUnavailableException(
    cause: Throwable,
) : IOException("Encryption key unavailable", cause)

/** AES-256-GCM through Tink. The keyset lives in SharedPreferences, encrypted
 *  by a master key in Android Keystore, and loads lazily on first use. */
class KeystoreAeadEncryption internal constructor(
    private val loadAead: () -> Aead,
) : AeadEncryption {
    constructor(context: Context) : this({ loadKeystoreAead(context.applicationContext) })

    // A throwing initializer leaves the lazy unset, so the next call retries the load.
    private val aead: Aead by lazy {
        try {
            loadAead()
        } catch (e: Exception) {
            throw KeyUnavailableException(e)
        }
    }

    /** Loads the key off the caller's thread so the first UI read does not block on it. */
    fun warmUp() {
        try {
            aead
        } catch (e: KeyUnavailableException) {
            log.w("encryption key unavailable at warm up", e)
        }
    }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.decrypt(ciphertext, associatedData)
}

private fun loadKeystoreAead(context: Context): Aead {
    AeadConfig.register()
    val manager =
        AndroidKeysetManager
            .Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREF_NAME)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
    if (!manager.isUsingKeystore) {
        log.w("keyset is stored unencrypted because Android Keystore is not in use")
    }
    return manager.keysetHandle.getPrimitive(Aead::class.java)
}
