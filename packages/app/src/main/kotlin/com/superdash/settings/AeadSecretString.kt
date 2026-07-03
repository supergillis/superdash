package com.superdash.settings

import android.util.Base64
import com.superdash.ha.security.AeadEncryption

/** [SecretString] backed by Tink AEAD (the same primitive that protects HA
 *  tokens). Encrypted values carry a version prefix so legacy plaintext values
 *  written before encryption still read back and are re-encrypted on next write. */
internal class AeadSecretString(
    private val encryption: AeadEncryption,
) : SecretString {
    override fun conceal(plain: String): String =
        if (plain.isEmpty()) {
            plain
        } else {
            PREFIX + Base64.encodeToString(encryption.encrypt(plain.encodeToByteArray()), Base64.NO_WRAP)
        }

    override fun reveal(stored: String): String {
        if (!stored.startsWith(PREFIX)) {
            // Legacy plaintext (or empty). Read as-is; re-encrypted on next write.
            return stored
        }
        return try {
            val cipher = Base64.decode(stored.removePrefix(PREFIX), Base64.NO_WRAP)
            encryption.decrypt(cipher).decodeToString()
        } catch (t: Throwable) {
            ""
        }
    }

    private companion object {
        const val PREFIX = "enc:v1:"
    }
}
