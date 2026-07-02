package com.superdash.esphome

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Abstracts the framed transport beneath [EsphomeConnection]. The connection
 *  treats every message as `(type, payload)` and never sees raw bytes again
 *  after the transport is built. */
internal interface EsphomeTransport {
    suspend fun readFrame(): EsphomeFrame

    suspend fun writeFrame(messageType: Int, payload: ByteArray)
}

internal class PlainTransport(
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    firstFrameConsumesPreamble: Boolean = false,
) : EsphomeTransport {
    private var preambleAlreadyRead = firstFrameConsumesPreamble

    override suspend fun readFrame(): EsphomeFrame {
        if (preambleAlreadyRead) {
            preambleAlreadyRead = false
            return input.readEsphomeFrameAfterPreamble()
        }
        return input.readEsphomeFrame()
    }

    override suspend fun writeFrame(messageType: Int, payload: ByteArray) {
        output.writeEsphomeFrame(messageType, payload)
    }
}

/** Post-handshake transport: every frame is one ChaCha20-Poly1305 record
 *  whose plaintext encodes `(type_u16_be, size_u16_be, protobuf_bytes)`.
 *
 *  The two cipher states come from the responder side of the Noise handshake.
 *  `recvCipher` decrypts initiator→responder traffic; `sendCipher` encrypts
 *  responder→initiator traffic. Each direction has its own nonce sequence
 *  inside the cipher state, so we don't manage nonces here. */
internal class NoiseTransport(
    private val input: ByteReadChannel,
    private val output: ByteWriteChannel,
    private val sendCipher: NoiseCipherState,
    private val recvCipher: NoiseCipherState,
) : EsphomeTransport {
    private val writeMutex = Mutex()

    override suspend fun readFrame(): EsphomeFrame {
        // 4-byte inner header (type u16 BE + size u16 BE) + payload + AEAD tag.
        val ciphertext = input.readNoiseFrame(maxPayloadLen = 4 + NOISE_MAX_PAYLOAD + recvCipher.macLength)
        val plain = recvCipher.decryptWithAd(EMPTY_AD, ciphertext)
        check(plain.size >= 4) { "Noise frame too short to contain header" }
        val type = ((plain[0].toInt() and 0xFF) shl 8) or (plain[1].toInt() and 0xFF)
        val size = ((plain[2].toInt() and 0xFF) shl 8) or (plain[3].toInt() and 0xFF)
        check(size == plain.size - 4) { "Noise frame size mismatch: header=$size, plaintext=${plain.size - 4}" }
        val payload = plain.copyOfRange(4, 4 + size)
        return EsphomeFrame(messageType = type, payload = payload)
    }

    override suspend fun writeFrame(messageType: Int, payload: ByteArray) {
        require(messageType in 0..0xFFFF) { "Noise messageType $messageType out of u16 range" }
        require(payload.size <= NOISE_MAX_PAYLOAD) { "Noise payload too large: ${payload.size}" }
        val plain = ByteArray(4 + payload.size)
        plain[0] = ((messageType ushr 8) and 0xFF).toByte()
        plain[1] = (messageType and 0xFF).toByte()
        plain[2] = ((payload.size ushr 8) and 0xFF).toByte()
        plain[3] = (payload.size and 0xFF).toByte()
        payload.copyInto(plain, destinationOffset = 4)
        writeMutex.withLock {
            val ciphertext = sendCipher.encryptWithAd(EMPTY_AD, plain)
            output.writeNoiseFrame(ciphertext)
        }
    }

    private companion object {
        private val EMPTY_AD: ByteArray = ByteArray(0)
    }
}
