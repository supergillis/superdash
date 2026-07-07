package com.superdash.esphome

import com.google.crypto.tink.subtle.X25519
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Byte-exact replay of the published `cacophony` test vector for
 *  `Noise_NNpsk0_25519_ChaChaPoly_SHA256`. The vector pins the prologue, PSK,
 *  both ephemeral private keys, every message payload, and every expected
 *  ciphertext. Our [NoiseSymmetricState] is run as both initiator and
 *  responder with those fixed inputs; any drift in HKDF, MixKey ordering,
 *  AEAD AD, or psk0 token handling fails this test at the exact step where
 *  the bug bites.
 *
 *  Source: cacophony.txt from the snow repo, last-modified commit
 *  2fcd3e2dbe3f4a455df5614538aa589c5854062b (fetched via
 *  https://raw.githubusercontent.com/mcginty/snow/main/tests/vectors/cacophony.txt),
 *  the entry whose `protocol_name` is `Noise_NNpsk0_25519_ChaChaPoly_SHA256`.
 *  Vector copy lives at `src/test/resources/com/superdash/esphome/cacophony-nnpsk0.json`. */
class EsphomeNoiseCacophonyTest {
    @Test
    fun `NNpsk0 transcript matches cacophony reference`() {
        val v = loadVector()
        assertEquals("Noise_NNpsk0_25519_ChaChaPoly_SHA256", v.protocol_name)
        assertEquals(1, v.init_psks.size)
        assertEquals(v.init_psks, v.resp_psks)
        assertEquals(v.init_prologue, v.resp_prologue)

        val psk = hex(v.init_psks[0])
        val prologue = hex(v.init_prologue)
        val initEphPriv = hex(v.init_ephemeral)
        val respEphPriv = hex(v.resp_ephemeral)
        val initEphPub = X25519.publicFromPrivate(initEphPriv)
        val respEphPub = X25519.publicFromPrivate(respEphPriv)

        // Both sides initialize.
        val init = NoiseSymmetricState()
        init.mixHash(prologue)
        init.mixKeyAndHash(psk)

        val resp = NoiseSymmetricState()
        resp.mixHash(prologue)
        resp.mixKeyAndHash(psk)

        // Message 0: initiator -> responder. Pattern token sequence: e, then
        // payload. psk0 modifier adds MixKey(e_pub) after MixHash on every
        // public key transmitted.
        val msg0 = v.messages[0]
        // Initiator side.
        init.mixHash(initEphPub)
        init.mixKey(initEphPub)
        val initEncrypted0 = init.encryptAndHash(hex(msg0.payload))
        val wire0 = initEphPub + initEncrypted0
        assertArrayEquals("message 0 ciphertext mismatch", hex(msg0.ciphertext), wire0)
        // Responder side processes the same bytes.
        val recvRe = wire0.copyOfRange(0, 32)
        assertArrayEquals(initEphPub, recvRe)
        resp.mixHash(recvRe)
        resp.mixKey(recvRe)
        val recvPayload0 = resp.decryptAndHash(wire0.copyOfRange(32, wire0.size))
        assertArrayEquals(hex(msg0.payload), recvPayload0)

        // Message 1: responder -> initiator. Tokens: e, ee, then payload.
        val msg1 = v.messages[1]
        // Responder side.
        resp.mixHash(respEphPub)
        resp.mixKey(respEphPub)
        val respEe = X25519.computeSharedSecret(respEphPriv, recvRe)
        resp.mixKey(respEe)
        val respEncrypted1 = resp.encryptAndHash(hex(msg1.payload))
        val wire1 = respEphPub + respEncrypted1
        assertArrayEquals("message 1 ciphertext mismatch", hex(msg1.ciphertext), wire1)
        // Initiator side processes message 1.
        val recvRespE = wire1.copyOfRange(0, 32)
        assertArrayEquals(respEphPub, recvRespE)
        init.mixHash(recvRespE)
        init.mixKey(recvRespE)
        val initEe = X25519.computeSharedSecret(initEphPriv, recvRespE)
        init.mixKey(initEe)
        val recvPayload1 = init.decryptAndHash(wire1.copyOfRange(32, wire1.size))
        assertArrayEquals(hex(msg1.payload), recvPayload1)

        // Handshake hash agreement.
        assertArrayEquals("handshake hash mismatch", hex(v.handshake_hash), init.handshakeHash)
        assertArrayEquals(init.handshakeHash, resp.handshakeHash)

        // Split.
        val initSplit = init.split() // (init->resp, resp->init) per spec
        val respSplit = resp.split()
        val initSendCipher = initSplit.first
        val initRecvCipher = initSplit.second
        val respRecvCipher = respSplit.first
        val respSendCipher = respSplit.second

        // Subsequent messages alternate: even index = init->resp, odd = resp->init.
        for (i in 2 until v.messages.size) {
            val m = v.messages[i]
            val payload = hex(m.payload)
            val expected = hex(m.ciphertext)
            if (i % 2 == 0) {
                val produced = initSendCipher.encryptWithAd(ByteArray(0), payload)
                assertArrayEquals("transport message $i (init->resp) mismatch", expected, produced)
                val decoded = respRecvCipher.decryptWithAd(ByteArray(0), produced)
                assertArrayEquals(payload, decoded)
            } else {
                val produced = respSendCipher.encryptWithAd(ByteArray(0), payload)
                assertArrayEquals("transport message $i (resp->init) mismatch", expected, produced)
                val decoded = initRecvCipher.decryptWithAd(ByteArray(0), produced)
                assertArrayEquals(payload, decoded)
            }
        }
    }

    private fun loadVector(): CacophonyVector {
        val stream =
            javaClass.getResourceAsStream("/com/superdash/esphome/cacophony-nnpsk0.json")
                ?: error("cacophony-nnpsk0.json not found on test classpath")
        val text = stream.bufferedReader().use { it.readText() }
        return JSON.decodeFromString(CacophonyVector.serializer(), text)
    }

    private companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    private data class CacophonyVector(
        val protocol_name: String,
        val init_prologue: String,
        val init_psks: List<String>,
        val init_ephemeral: String,
        val resp_prologue: String,
        val resp_psks: List<String>,
        val resp_ephemeral: String,
        val handshake_hash: String,
        val messages: List<CacophonyMessage>,
    )

    @Serializable
    private data class CacophonyMessage(
        val payload: String,
        val ciphertext: String,
    )

    private fun hex(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "odd-length hex: $hex" }
        return ByteArray(hex.length / 2) { i ->
            ((Character.digit(hex[2 * i], 16) shl 4) or Character.digit(hex[2 * i + 1], 16)).toByte()
        }
    }
}
