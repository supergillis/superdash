package com.superdash.esphome

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readByte
import io.ktor.utils.io.readByteArray
import io.ktor.utils.io.writeByteArray
import kotlinx.io.Buffer
import kotlinx.io.Source
import kotlinx.io.readByteArray

internal data class EsphomeFrame(
    val messageType: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is EsphomeFrame) {
            return false
        }
        return messageType == other.messageType && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * messageType + payload.contentHashCode()
}

/** LEB128-encode an unsigned-ish 32-bit value. Negative inputs are written as
 *  if reinterpreted unsigned (consistent with ESPHome's protobuf use). */
internal fun encodeVarint(value: Int): ByteArray {
    val buffer = Buffer()
    var remaining = value.toLong() and 0xFFFFFFFFL
    while (remaining and 0x7FL.inv() != 0L) {
        buffer.writeByte(((remaining and 0x7FL) or 0x80L).toByte())
        remaining = remaining ushr 7
    }
    buffer.writeByte(remaining.toByte())
    return buffer.readByteArray()
}

internal fun Source.readVarint(): Int {
    var result = 0
    var shift = 0
    while (true) {
        check(shift <= 28) { "varint too large for Int32" }
        val byte = readByte().toInt() and 0xFF
        result = result or ((byte and 0x7F) shl shift)
        if (byte and 0x80 == 0) {
            return result
        }
        shift += 7
    }
}

internal fun encodeFrame(
    messageType: Int,
    payload: ByteArray,
): ByteArray {
    val buffer = Buffer()
    buffer.writeByte(0x00)
    buffer.write(encodeVarint(payload.size))
    buffer.write(encodeVarint(messageType))
    buffer.write(payload)
    return buffer.readByteArray()
}

/** Read one whole frame off a `Source`. Returns `null` at EOF. Throws on
 *  preamble != 0x00. The encrypted variant uses 0x01 and is rejected. */
internal suspend fun Source.readFrame(): EsphomeFrame? {
    if (exhausted()) {
        return null
    }
    val preamble = readByte().toInt() and 0xFF
    check(preamble == 0x00) { "Unsupported frame preamble: $preamble (encrypted streams are out of MVP scope)" }
    val length = readVarint()
    require(length in 0..NOISE_MAX_PAYLOAD) { "ESPHome frame length out of range: $length" }
    val messageType = readVarint()
    val payload = readByteArray(length)
    return EsphomeFrame(messageType, payload)
}

/** Read one whole frame off a Ktor `ByteReadChannel`. Mirrors `Source.readFrame`
 *  but using channel I/O so connection coroutines suspend on socket reads. */
internal suspend fun ByteReadChannel.readEsphomeFrame(): EsphomeFrame {
    val preamble = readByte().toInt() and 0xFF
    check(preamble == 0x00) { "Unsupported frame preamble: $preamble" }
    val length = readVarintFromChannel()
    require(length in 0..NOISE_MAX_PAYLOAD) { "ESPHome frame length out of range: $length" }
    val messageType = readVarintFromChannel()
    val payload = readByteArray(length)
    return EsphomeFrame(messageType, payload)
}

/** Read a frame whose preamble byte has already been consumed by the caller.
 *  Used by the connection dispatcher in [EsphomeServer] which peeks the
 *  preamble to choose between plain and Noise transports. */
internal suspend fun ByteReadChannel.readEsphomeFrameAfterPreamble(): EsphomeFrame {
    val length = readVarintFromChannel()
    require(length in 0..NOISE_MAX_PAYLOAD) { "ESPHome frame length out of range: $length" }
    val messageType = readVarintFromChannel()
    val payload = readByteArray(length)
    return EsphomeFrame(messageType, payload)
}

private suspend fun ByteReadChannel.readVarintFromChannel(): Int {
    var result = 0
    var shift = 0
    while (true) {
        check(shift <= 28) { "varint too large for Int32" }
        val byte = readByte().toInt() and 0xFF
        result = result or ((byte and 0x7F) shl shift)
        if (byte and 0x80 == 0) {
            return result
        }
        shift += 7
    }
}

internal suspend fun ByteWriteChannel.writeEsphomeFrame(
    messageType: Int,
    payload: ByteArray,
) {
    writeByteArray(encodeFrame(messageType, payload))
    flush()
}
