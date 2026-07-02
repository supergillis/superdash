package com.superdash.esphome

import com.google.crypto.tink.subtle.X25519
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readByte
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.esphome.api.HelloRequest
import org.esphome.api.HelloResponse
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EsphomeNoiseConnectionTest {
    private val deviceInfo =
        EsphomeDeviceInfo(
            name = "superdash-test",
            macAddress = "AA:BB:CC:DD:EE:FF",
            esphomeVersion = "2026.4.5-superdash",
            compilationTime = "",
            model = "Pixel emulator",
            manufacturer = "Google",
            friendlyName = "Superdash Test",
        )

    @Test
    fun `HelloRequest round trips over Noise transport`() =
        runTest(UnconfinedTestDispatcher()) {
            val psk = ByteArray(32) { (it + 1).toByte() }
            val c2s = ByteChannel(autoFlush = true)
            val s2c = ByteChannel(autoFlush = true)

            val serverHandshake =
                async {
                    val preamble = c2s.readByte().toInt() and 0xFF
                    check(preamble == NOISE_PREAMBLE)
                    performNoiseHandshake(
                        input = c2s,
                        output = s2c,
                        psk = psk,
                        serverName = deviceInfo.name,
                        macAddress = deviceInfo.macAddress,
                    )
                }

            // Drive an initiator (same pattern as EsphomeNoiseTransportTest).
            val clientSym = NoiseSymmetricState()
            clientSym.mixHash(NOISE_PROLOGUE)
            clientSym.mixKeyAndHash(psk)
            val ePriv = X25519.generatePrivateKey()
            val ePub = X25519.publicFromPrivate(ePriv)
            clientSym.mixHash(ePub)
            clientSym.mixKey(ePub)
            val encryptedEmpty = clientSym.encryptAndHash(ByteArray(0))
            val msg1 =
                ByteArray(1 + ePub.size + encryptedEmpty.size).also { buf ->
                    buf[0] = NOISE_HANDSHAKE_OK
                    System.arraycopy(ePub, 0, buf, 1, ePub.size)
                    System.arraycopy(encryptedEmpty, 0, buf, 1 + ePub.size, encryptedEmpty.size)
                }
            c2s.writeNoiseFrame(msg1)

            val msg2 = s2c.readNoiseFrame(maxPayloadLen = 256)
            val serverEPub = msg2.copyOfRange(1, 33)
            clientSym.mixHash(serverEPub)
            clientSym.mixKey(serverEPub)
            val shared = X25519.computeSharedSecret(ePriv, serverEPub)
            clientSym.mixKey(shared)
            clientSym.decryptAndHash(msg2.copyOfRange(33, msg2.size))
            val clientSplit = clientSym.split()
            val initToResp = clientSplit.first
            val respToInit = clientSplit.second

            val serverResult = serverHandshake.await()
            val serverTransport =
                NoiseTransport(
                    input = c2s,
                    output = s2c,
                    sendCipher = serverResult.sendCipher,
                    recvCipher = serverResult.recvCipher,
                )

            val job =
                launch {
                    EsphomeConnection(
                        transport = serverTransport,
                        deviceInfo = deviceInfo,
                        entities = emptyList(),
                    ).run()
                }

            // Encrypt and send a HelloRequest using the transport plaintext layout.
            val helloPayload =
                HelloRequest
                    .newBuilder()
                    .setClientInfo("test-client")
                    .build()
                    .toByteArray()
            val helloPlaintext =
                ByteArray(4 + helloPayload.size).also { buf ->
                    buf[0] = (EsphomeMessageType.HELLO_REQUEST ushr 8 and 0xFF).toByte()
                    buf[1] = (EsphomeMessageType.HELLO_REQUEST and 0xFF).toByte()
                    buf[2] = (helloPayload.size ushr 8 and 0xFF).toByte()
                    buf[3] = (helloPayload.size and 0xFF).toByte()
                    System.arraycopy(helloPayload, 0, buf, 4, helloPayload.size)
                }
            val helloCt = initToResp.encryptWithAd(ByteArray(0), helloPlaintext)
            c2s.writeNoiseFrame(helloCt)

            val respWire = s2c.readNoiseFrame(maxPayloadLen = NOISE_MAX_PAYLOAD + 16)
            val respPlain = respToInit.decryptWithAd(ByteArray(0), respWire)
            val type = ((respPlain[0].toInt() and 0xFF) shl 8) or (respPlain[1].toInt() and 0xFF)
            val size = ((respPlain[2].toInt() and 0xFF) shl 8) or (respPlain[3].toInt() and 0xFF)
            assertEquals(EsphomeMessageType.HELLO_RESPONSE, type)
            val parsed = HelloResponse.parseFrom(respPlain.copyOfRange(4, 4 + size))
            assertEquals(1, parsed.apiVersionMajor)
            assertEquals(14, parsed.apiVersionMinor)

            c2s.close()
            s2c.close()
            job.cancel()
        }
}
