package com.superdash.esphome

import com.google.crypto.tink.subtle.X25519
import com.superdash.core.log.Log
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel

private val log = Log("EsphomeNoiseHandshake")

/** Cap on the inbound handshake message size. NNpsk0 message 1 is 1 header
 *  byte + 32-byte ephemeral + 16-byte AEAD tag = 49 bytes for ESPHome
 *  (empty initiator payload). 256 leaves headroom for future payload growth
 *  but stops a malicious peer from forcing us to allocate large buffers. */
private const val NOISE_HANDSHAKE_MAX = 256

/** Run the Noise_NNpsk0_25519_ChaChaPoly_SHA256 responder handshake over
 *  [input]/[output]. On success returns the two transport CipherStates and the
 *  final handshake hash (useful for binding application-level identities).
 *
 *  Caller MUST have already consumed the leading 0x01 preamble byte.
 *
 *  On any error the responder attempts to send a `NOISE_HANDSHAKE_FAIL` frame
 *  with the error message as UTF-8 before rethrowing, matching ESPHome's
 *  api_frame_helper_noise.cpp behaviour. */
internal suspend fun performNoiseHandshake(
    input: ByteReadChannel,
    output: ByteWriteChannel,
    psk: ByteArray,
    serverName: String,
    macAddress: String,
): EsphomeNoiseHandshakeResult {
    require(psk.size == 32) { "Noise PSK must be 32 bytes (got ${psk.size})" }

    val sym = NoiseSymmetricState()
    sym.mixHash(NOISE_PROLOGUE)

    try {
        // --- Read initiator message 1: -> psk, e ---
        val frame1 = input.readNoiseFrameAfterPreamble(maxPayloadLen = NOISE_HANDSHAKE_MAX)
        check(frame1.isNotEmpty()) { "Empty Noise hello" }
        check(frame1[0] == NOISE_HANDSHAKE_OK) {
            val reason = frame1.copyOfRange(1, frame1.size).toString(Charsets.UTF_8)
            "Peer reported handshake failure: $reason"
        }
        check(frame1.size >= 1 + 32 + 16) {
            "Noise message 1 too short: ${frame1.size} (need 1 header + 32 ephemeral + 16 AEAD tag)"
        }

        sym.mixKeyAndHash(psk)
        val re = frame1.copyOfRange(1, 1 + 32)
        sym.mixHash(re)
        sym.mixKey(re) // PSK pattern: also mix the ephemeral as key
        val encryptedPayload1 = frame1.copyOfRange(1 + 32, frame1.size)
        sym.decryptAndHash(encryptedPayload1) // ESPHome initiator payload is empty + 16-byte tag

        // --- Write responder message 2: <- e, ee ---
        val ePriv = X25519.generatePrivateKey()
        val ePub = X25519.publicFromPrivate(ePriv)
        sym.mixHash(ePub)
        sym.mixKey(ePub) // PSK pattern

        val shared = X25519.computeSharedSecret(ePriv, re)
        sym.mixKey(shared)

        val nameBytes = serverName.toByteArray(Charsets.UTF_8)
        val macBytes = macAddress.toByteArray(Charsets.UTF_8)
        // aioesphomeapi expects: 0x01 | <name UTF-8> 0x00 | <mac ASCII> 0x00
        val serverHello = ByteArray(1 + nameBytes.size + 1 + macBytes.size + 1)
        serverHello[0] = 0x01
        System.arraycopy(nameBytes, 0, serverHello, 1, nameBytes.size)
        // position 1 + nameBytes.size is already 0 from zero-fill
        System.arraycopy(macBytes, 0, serverHello, 1 + nameBytes.size + 1, macBytes.size)
        // position 1 + nameBytes.size + 1 + macBytes.size is already 0 from zero-fill
        val encryptedPayload2 = sym.encryptAndHash(serverHello)

        val msg2Body = ByteArray(1 + ePub.size + encryptedPayload2.size)
        msg2Body[0] = NOISE_HANDSHAKE_OK
        System.arraycopy(ePub, 0, msg2Body, 1, ePub.size)
        System.arraycopy(encryptedPayload2, 0, msg2Body, 1 + ePub.size, encryptedPayload2.size)
        output.writeNoiseFrame(msg2Body)

        val (c1, c2) = sym.split()
        log.i("noise handshake ok", "serverName" to serverName)
        // For the responder: c1 decrypts initiator -> responder; c2 encrypts responder -> initiator.
        return EsphomeNoiseHandshakeResult(
            sendCipher = c2,
            recvCipher = c1,
            handshakeHash = sym.handshakeHash,
        )
    } catch (t: Throwable) {
        log.w("noise handshake failed", t)
        runCatching {
            val reason = "handshake failed".toByteArray(Charsets.UTF_8)
            val failBody = ByteArray(reason.size + 1)
            failBody[0] = NOISE_HANDSHAKE_FAIL
            System.arraycopy(reason, 0, failBody, 1, reason.size)
            output.writeNoiseFrame(failBody)
        }
        throw t
    }
}
