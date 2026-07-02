package com.superdash.ha.security

import com.google.crypto.tink.Aead

/** Thin wrapper that owns the AEAD primitive. The keyset is loaded lazily on
 *  first use to keep app startup fast. */
class AeadEncryption(
    private val keys: KeystoreKeyProvider,
) {
    private val aead: Aead by lazy { keys.getOrCreateAead() }

    fun encrypt(plaintext: ByteArray, associatedData: ByteArray = byteArrayOf()): ByteArray =
        aead.encrypt(plaintext, associatedData)

    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray = byteArrayOf()): ByteArray =
        aead.decrypt(ciphertext, associatedData)
}
