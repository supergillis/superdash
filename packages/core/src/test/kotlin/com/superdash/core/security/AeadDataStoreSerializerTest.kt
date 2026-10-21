package com.superdash.core.security

import com.google.crypto.tink.Aead
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.GeneralSecurityException
import java.security.ProviderException

class AeadDataStoreSerializerTest {
    private class FakeAead(
        private val decryptFailure: GeneralSecurityException? = null,
    ) : Aead {
        override fun encrypt(plaintext: ByteArray, associatedData: ByteArray): ByteArray = plaintext

        override fun decrypt(ciphertext: ByteArray, associatedData: ByteArray): ByteArray =
            decryptFailure?.let { throw it } ?: ciphertext
    }

    private class Case(
        val name: String,
        val loadAead: () -> Aead,
        val stored: String,
    )

    private fun describe(result: Result<String?>): String {
        val failure = result.exceptionOrNull() ?: return "value ${result.getOrNull()}"
        return "${failure::class.simpleName} caused by ${failure.cause?.let { it::class.simpleName }}"
    }

    @Test
    fun `read classifies failures by the step that failed`() =
        runTest {
            val cases =
                listOf(
                    Case("valid", { FakeAead() }, "\"secret\""),
                    Case("key load fails", { throw GeneralSecurityException("keyset unreadable") }, "\"secret\""),
                    Case("key load provider error", { throw ProviderException("keystore busy") }, "\"secret\""),
                    Case("decrypt fails", { FakeAead(GeneralSecurityException("bad tag")) }, "\"secret\""),
                    Case("bad json", { FakeAead() }, "{not json"),
                )

            val outcomes =
                cases.map { case ->
                    val serializer =
                        AeadDataStoreSerializer(
                            encryption = KeystoreAeadEncryption(case.loadAead),
                            encode = { value: String -> Json.encodeToString(value).encodeToByteArray() },
                            decode = { bytes -> Json.decodeFromString<String>(bytes.decodeToString()) },
                        )
                    case.name to describe(runCatching { serializer.readFrom(case.stored.encodeToByteArray().inputStream()) })
                }

            assertEquals(
                listOf(
                    "valid" to "value secret",
                    "key load fails" to "KeyUnavailableException caused by GeneralSecurityException",
                    "key load provider error" to "KeyUnavailableException caused by ProviderException",
                    "decrypt fails" to "value null",
                    "bad json" to "value null",
                ),
                outcomes,
            )
        }
}
