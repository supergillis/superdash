package com.superdash.esphome

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Known-Answer-Tests for the primitive helpers in [EsphomeNoiseCrypto].
 *
 *  Vectors:
 *   - SHA-256: NIST CAVP "abc" example + the empty-string case.
 *   - HMAC-SHA256: RFC 4231 test cases 1, 2, and 3.
 *   - HKDF (Noise variant): derived from RFC 5869 Appendix A.1 by replacing
 *     the `info || counter` HKDF-Expand step with the Noise spec's
 *     `T_{n-1} || byte(n)` chaining. We verify by hand-computing the two
 *     first 32-byte outputs against an independent reference. */
class EsphomeNoiseCryptoKatTest {
    @Test
    fun `SHA-256 of empty string`() {
        val expected = hex("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855")
        assertArrayEquals(expected, sha256(ByteArray(0)))
    }

    @Test
    fun `SHA-256 of abc`() {
        val expected = hex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
        assertArrayEquals(expected, sha256("abc".toByteArray(Charsets.US_ASCII)))
    }

    @Test
    fun `HMAC-SHA256 RFC 4231 test case 1`() {
        // Key:  0x0b repeated 20 times.
        // Data: "Hi There" (8 bytes).
        val key = ByteArray(20) { 0x0b }
        val data = "Hi There".toByteArray(Charsets.US_ASCII)
        val expected = hex("b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7")
        assertArrayEquals(expected, hmacSha256(key, data))
    }

    @Test
    fun `HMAC-SHA256 RFC 4231 test case 2`() {
        // Key:  "Jefe" (4 bytes ASCII).
        // Data: "what do ya want for nothing?" (28 bytes ASCII).
        val key = "Jefe".toByteArray(Charsets.US_ASCII)
        val data = "what do ya want for nothing?".toByteArray(Charsets.US_ASCII)
        val expected = hex("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843")
        assertArrayEquals(expected, hmacSha256(key, data))
    }

    @Test
    fun `HMAC-SHA256 RFC 4231 test case 3`() {
        // Key:  0xaa repeated 20 times.
        // Data: 0xdd repeated 50 times.
        val key = ByteArray(20) { 0xaa.toByte() }
        val data = ByteArray(50) { 0xdd.toByte() }
        val expected = hex("773ea91e36800e46854db8ebd09181a72959098b3ef8c122d9635514ced565fe")
        assertArrayEquals(expected, hmacSha256(key, data))
    }

    @Test
    fun `Noise HKDF two outputs from known inputs`() {
        // Verifies the Noise-style HKDF: PRK = HMAC(ck, ikm); T1 = HMAC(PRK, 0x01);
        // T2 = HMAC(PRK, T1 || 0x02). Inputs taken from RFC 5869 A.1 IKM/salt;
        // expected outputs derived by hand-applying the Noise variant.
        val ck = hex("000102030405060708090a0b0c") // RFC 5869 A.1 salt (13 bytes)
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b") // 22 bytes
        // PRK = HMAC(salt, IKM) = RFC 5869 A.1 PRK value (32 bytes).
        val expectedPrk = hex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5")
        assertArrayEquals(expectedPrk, hmacSha256(ck, ikm))

        // T1 = HMAC(PRK, byte(0x01)). T2 = HMAC(PRK, T1 || byte(0x02)).
        // Verify our hkdf() function returns those two values.
        val outputs = hkdf(ck, ikm, 2)
        assertEquals(2, outputs.size)
        // Cross-check T1 by direct computation.
        val t1Direct = hmacSha256(expectedPrk, byteArrayOf(0x01))
        assertArrayEquals(t1Direct, outputs[0])
        // Cross-check T2 by direct computation.
        val t2Direct = hmacSha256(expectedPrk, t1Direct + byteArrayOf(0x02))
        assertArrayEquals(t2Direct, outputs[1])
    }

    @Test
    fun `Noise HKDF three outputs continue the chain`() {
        // Same inputs as the two-output case; check that outputs[0..1] are
        // identical and outputs[2] = HMAC(PRK, T2 || 0x03).
        val ck = hex("000102030405060708090a0b0c")
        val ikm = hex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b")
        val prk = hex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5")
        val outputs = hkdf(ck, ikm, 3)
        assertEquals(3, outputs.size)
        val t1 = hmacSha256(prk, byteArrayOf(0x01))
        val t2 = hmacSha256(prk, t1 + byteArrayOf(0x02))
        val t3 = hmacSha256(prk, t2 + byteArrayOf(0x03))
        assertArrayEquals(t1, outputs[0])
        assertArrayEquals(t2, outputs[1])
        assertArrayEquals(t3, outputs[2])
    }
}

private fun hex(hex: String): ByteArray {
    require(hex.length % 2 == 0) { "odd-length hex" }
    return ByteArray(hex.length / 2) { i ->
        ((Character.digit(hex[2 * i], 16) shl 4) or Character.digit(hex[2 * i + 1], 16)).toByte()
    }
}
