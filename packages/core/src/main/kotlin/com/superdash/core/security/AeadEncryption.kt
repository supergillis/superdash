package com.superdash.core.security

import com.google.crypto.tink.Aead

interface AeadEncryption {
    fun encrypt(plaintext: ByteArray, associatedData: ByteArray = byteArrayOf()): ByteArray

    fun decrypt(ciphertext: ByteArray, associatedData: ByteArray = byteArrayOf()): ByteArray
}

/** Owns the Tink AEAD primitive. The keyset is loaded lazily on first use to
 *  keep app startup fast. */
class KeystoreAeadEncryption(
    private val keys: KeystoreKeyProvider,
) : AeadEncryption {
    private val aead: Aead by lazy { keys.getOrCreateAead() }

    override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.encrypt(plaintext, associatedData)

    override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
        aead.decrypt(ciphertext, associatedData)
}
