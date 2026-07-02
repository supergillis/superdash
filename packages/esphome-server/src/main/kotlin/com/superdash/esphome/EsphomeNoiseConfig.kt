package com.superdash.esphome

/** Runtime selection between plaintext and Noise. Mirrors ESPHome firmware:
 *  a device with no PSK accepts only plaintext; a device with a PSK accepts
 *  only Noise. We never accept both at the same time on one socket — the
 *  preamble byte the peer sends decides which transport we build, and a
 *  mismatched preamble is closed without a reply. */
internal sealed interface EsphomeNoiseConfig {
    data object PlainOnly : EsphomeNoiseConfig

    data class NoiseOnly(
        val psk: ByteArray,
    ) : EsphomeNoiseConfig {
        init {
            require(psk.size == 32) { "Noise PSK must be 32 bytes (got ${psk.size})" }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) {
                return true
            }
            if (other !is NoiseOnly) {
                return false
            }
            return psk.contentEquals(other.psk)
        }

        override fun hashCode(): Int = psk.contentHashCode()
    }
}
