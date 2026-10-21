package com.superdash.esphome

/** Deterministic 6-byte MAC-style string derived from a per-app stable id.
 *  ESPHome's mDNS TXT requires `mac` and HA's `esphome` integration uses a
 *  formatted version of it as the config-entry unique_id. We synthesize a
 *  locally-administered (bit 1 of first octet set), unicast (bit 0 cleared)
 *  MAC from the hex digits of the input, so UUID dashes are ignored. Android
 *  does not expose a real MAC since 6.0.
 *
 *  Caveat for callers: if the input id rotates (factory reset, app
 *  reinstall, `pm clear`), HA sees a "different device" and orphans entity
 *  history. This is inherent to using a per-app stable id; a future stable
 *  user-set device identifier (e.g. paired-once token) would be a better
 *  source. */
internal fun deriveMacFromStableId(stableId: String): String {
    val bytes =
        stableId
            .filter { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }
            .padEnd(MAC_HEX_LENGTH, '0')
            .take(MAC_HEX_LENGTH)
            .hexToByteArray()
    bytes[0] = ((bytes[0].toInt() or 0x02) and 0xFE).toByte()
    return bytes.toHexString(MAC_FORMAT)
}

private const val MAC_HEX_LENGTH = 12

private val MAC_FORMAT =
    HexFormat {
        upperCase = true
        bytes.byteSeparator = ":"
    }
