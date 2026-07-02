package com.superdash.esphome

import com.google.crypto.tink.aead.internal.InsecureNonceChaCha20Poly1305
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Noise protocol name for the responder pattern we implement. The string is
 *  exactly 36 ASCII bytes; per Noise spec section 5.2, since this exceeds
 *  HASHLEN (32 for SHA-256), the SymmetricState initialises `h = SHA256(name)`. */
private const val NOISE_PROTOCOL_NAME = "Noise_NNpsk0_25519_ChaChaPoly_SHA256"

/** SHA-256 produces 32-byte outputs; ChaCha20 keys are also 32 bytes. */
private const val HASHLEN = 32

/** Poly1305 tag length appended to ChaCha20-Poly1305 ciphertexts. */
private const val AEAD_TAG_LEN = 16

internal class NoiseCipherState(
    private val key: ByteArray,
) {
    init {
        require(key.size == HASHLEN) { "ChaCha20 key must be 32 bytes" }
    }

    private val aead = InsecureNonceChaCha20Poly1305(key)
    private var counter: Long = 0
    internal val macLength: Int = AEAD_TAG_LEN

    internal fun encryptWithAd(ad: ByteArray, plaintext: ByteArray): ByteArray {
        val ct = aead.encrypt(noiseNonce(counter), plaintext, ad)
        counter++
        return ct
    }

    internal fun decryptWithAd(ad: ByteArray, ciphertext: ByteArray): ByteArray {
        val pt = aead.decrypt(noiseNonce(counter), ciphertext, ad)
        counter++
        return pt
    }
}

internal class NoiseSymmetricState {
    private var ck: ByteArray
    private var h: ByteArray
    private var cipher: NoiseCipherState? = null

    init {
        val nameBytes = NOISE_PROTOCOL_NAME.toByteArray(Charsets.US_ASCII)
        // protocol_name is 36 bytes > HASHLEN(32), so hash it down.
        h = sha256(nameBytes)
        ck = h.copyOf()
    }

    internal val handshakeHash: ByteArray
        get() = h.copyOf()

    internal fun mixHash(data: ByteArray) {
        val buf = ByteArray(h.size + data.size)
        System.arraycopy(h, 0, buf, 0, h.size)
        System.arraycopy(data, 0, buf, h.size, data.size)
        h = sha256(buf)
    }

    internal fun mixKey(input: ByteArray) {
        val out = hkdf(ck, input, 2)
        ck = out[0]
        cipher = NoiseCipherState(out[1])
    }

    internal fun mixKeyAndHash(input: ByteArray) {
        val out = hkdf(ck, input, 3)
        ck = out[0]
        mixHash(out[1])
        cipher = NoiseCipherState(out[2])
    }

    internal fun encryptAndHash(plaintext: ByteArray): ByteArray {
        val current = cipher
        return if (current != null) {
            // AD is `h` BEFORE the subsequent MixHash of the ciphertext.
            val ct = current.encryptWithAd(h, plaintext)
            mixHash(ct)
            ct
        } else {
            mixHash(plaintext)
            plaintext
        }
    }

    internal fun decryptAndHash(ciphertext: ByteArray): ByteArray {
        val current = cipher
        return if (current != null) {
            val pt = current.decryptWithAd(h, ciphertext)
            mixHash(ciphertext)
            pt
        } else {
            mixHash(ciphertext)
            ciphertext
        }
    }

    internal fun split(): Pair<NoiseCipherState, NoiseCipherState> {
        val out = hkdf(ck, ByteArray(0), 2)
        return NoiseCipherState(out[0]) to NoiseCipherState(out[1])
    }
}

/** Noise nonce: 4 zero bytes followed by `counter` as little-endian uint64. */
private fun noiseNonce(counter: Long): ByteArray {
    val nonce = ByteArray(12)
    ByteBuffer.wrap(nonce, 4, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(counter)
    return nonce
}

internal fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

internal fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    return mac.doFinal(data)
}

/** HKDF (RFC 5869) using SHA-256, returning [outputs] independent 32-byte
 *  chunks chained per Noise spec section 4.3. */
internal fun hkdf(chainingKey: ByteArray, ikm: ByteArray, outputs: Int): Array<ByteArray> {
    require(outputs in 2..3) { "HKDF outputs must be 2 or 3 in Noise, got $outputs" }
    val prk = hmacSha256(chainingKey, ikm)
    val t1 = hmacSha256(prk, byteArrayOf(0x01))
    val t2Input =
        ByteArray(t1.size + 1).also {
            System.arraycopy(t1, 0, it, 0, t1.size)
            it[t1.size] = 0x02
        }
    val t2 = hmacSha256(prk, t2Input)
    if (outputs == 2) {
        return arrayOf(t1, t2)
    }
    val t3Input =
        ByteArray(t2.size + 1).also {
            System.arraycopy(t2, 0, it, 0, t2.size)
            it[t2.size] = 0x03
        }
    val t3 = hmacSha256(prk, t3Input)
    return arrayOf(t1, t2, t3)
}
