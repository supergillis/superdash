package com.superdash.esphome

internal data class EsphomeNoiseHandshakeResult(
    val sendCipher: NoiseCipherState,
    val recvCipher: NoiseCipherState,
    val handshakeHash: ByteArray,
)
