package com.superdash.esphome

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.writeByteArray

internal const val NOISE_PREAMBLE: Int = 0x01

/** Prologue mixed into the Noise handshake hash by both sides. The
 *  trailing two zero bytes match ESPHome firmware (api_frame_helper_noise.cpp)
 *  and aioesphomeapi (noise.py). */
internal val NOISE_PROLOGUE: ByteArray =
    "NoiseAPIInit\u0000\u0000".toByteArray(Charsets.US_ASCII)

/** Header byte that prefixes the Noise handshake payload only. See aioesphomeapi
 *  `noise.py::_perform_handshake`. The byte is 0x00 to indicate "no error /
 *  hello". A value of 0x01 means the peer rejected the handshake and the rest
 *  of the payload is a UTF-8 error message. */
internal const val NOISE_HANDSHAKE_OK: Byte = 0x00
internal const val NOISE_HANDSHAKE_FAIL: Byte = 0x01

/** Max plaintext payload after Noise transport decrypt. ESPHome firmware uses
 *  ~16 KiB; we pick the same so an oversize frame trips the guard. */
internal const val NOISE_MAX_PAYLOAD: Int = 16 * 1024

internal suspend fun ByteWriteChannel.writeNoiseFrame(payload: ByteArray) {
    require(payload.size <= 0xFFFF) { "Noise frame payload too large: ${payload.size}" }
    val frame = ByteArray(3 + payload.size)
    frame[0] = NOISE_PREAMBLE.toByte()
    frame[1] = ((payload.size ushr 8) and 0xFF).toByte()
    frame[2] = (payload.size and 0xFF).toByte()
    payload.copyInto(frame, destinationOffset = 3)
    writeByteArray(frame)
    flush()
}

/** Read one Noise frame body. [maxPayloadLen] caps the declared length so a
 *  malicious peer cannot make us allocate a 64 KiB buffer; pass
 *  [NOISE_MAX_PAYLOAD] + AEAD tag overhead for transport frames. */
internal suspend fun ByteReadChannel.readNoiseFrame(maxPayloadLen: Int): ByteArray {
    val preamble = readByte().toInt() and 0xFF
    check(preamble == NOISE_PREAMBLE) { "Expected Noise preamble 0x01, got 0x${preamble.toString(16)}" }
    val hi = readByte().toInt() and 0xFF
    val lo = readByte().toInt() and 0xFF
    val length = (hi shl 8) or lo
    check(length <= maxPayloadLen) { "Noise payload length $length exceeds cap $maxPayloadLen" }
    return readByteArray(length)
}

/** Read a Noise frame whose preamble byte has already been consumed by the
 *  caller. */
internal suspend fun ByteReadChannel.readNoiseFrameAfterPreamble(maxPayloadLen: Int): ByteArray {
    val hi = readByte().toInt() and 0xFF
    val lo = readByte().toInt() and 0xFF
    val length = (hi shl 8) or lo
    check(length <= maxPayloadLen) { "Noise payload length $length exceeds cap $maxPayloadLen" }
    return readByteArray(length)
}
