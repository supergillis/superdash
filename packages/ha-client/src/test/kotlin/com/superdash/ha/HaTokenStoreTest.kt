package com.superdash.ha

import com.superdash.core.security.AeadEncryption
import com.superdash.core.security.KeyUnavailableException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HaTokenStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val tokens = HaTokens(accessToken = "access", refreshToken = "refresh", expiresAtEpochMs = 1_000L)

    /** Plaintext passthrough that reports the key as unavailable for the first [outages] calls. */
    private class RecoveringEncryption(
        private var outages: Int,
    ) : AeadEncryption {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = plaintext

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray {
            if (outages > 0) {
                outages--
                throw KeyUnavailableException(IllegalStateException("keystore locked"))
            }
            return ciphertext
        }
    }

    private fun storeWithSavedTokens(encryption: AeadEncryption): HaTokenStore {
        val file = folder.newFile("ha_secrets.bin")
        file.writeText(haJson.encodeToString(HaTokens.serializer(), tokens))
        return HaTokenStore(encryption, file, CoroutineScope(Dispatchers.IO + Job()))
    }

    @Test
    fun `tokens flow waits out an unavailable key and then emits the stored tokens`() =
        runTest {
            val store = storeWithSavedTokens(RecoveringEncryption(outages = 2))

            val emitted = store.tokensFlow.first()

            assertEquals(tokens, emitted)
            assertEquals(3_000L, currentTime)
        }

    @Test
    fun `clear succeeds while the key is unavailable`() =
        runTest {
            val store = storeWithSavedTokens(RecoveringEncryption(outages = Int.MAX_VALUE))

            store.clear()

            assertEquals(null, store.tokensFlow.first())
        }
}
