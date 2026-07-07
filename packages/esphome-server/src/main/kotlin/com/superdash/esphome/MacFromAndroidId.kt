package com.superdash.esphome

/** Deterministic 6-byte MAC-style string derived from a per-app stable id.
 *  ESPHome's mDNS TXT requires `mac` and HA's `esphome` integration uses a
 *  formatted version of it as the config-entry unique_id. We synthesize a
 *  locally-administered (bit 1 of first octet set), unicast (bit 0 cleared)
 *  MAC from the input. Android does not expose a real MAC since 6.0.
 *
 *  Caveat for callers: if the input id rotates (factory reset, app
 *  reinstall, `pm clear`), HA sees a "different device" and orphans entity
 *  history. This is inherent to using a per-app stable id; a future stable
 *  user-set device identifier (e.g. paired-once token) would be a better
 *  source. */
internal fun deriveMacFromStableId(stableId: String): String {
    val padded = stableId.padEnd(12, '0').take(12).uppercase()
    val bytes =
        IntArray(6) { i ->
            val pair = padded.substring(i * 2, i * 2 + 2)
            pair.toIntOrNull(radix = 16) ?: 0
        }
    // First octet: set bit 1 (locally administered), clear bit 0 (unicast).
    bytes[0] = (bytes[0] or 0x02) and 0xFE
    return bytes.joinToString(":") { it.toString(16).padStart(2, '0').uppercase() }
}
