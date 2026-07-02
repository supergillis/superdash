package com.superdash.esphome

import com.google.crypto.tink.subtle.X25519
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.readByte
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EsphomeNoiseTransportTest {
    private val psk = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun `transport round trips a frame in each direction`() =
        runTest(UnconfinedTestDispatcher()) {
            val c2s = ByteChannel(autoFlush = true)
            val s2c = ByteChannel(autoFlush = true)

            val serverFut =
                async {
                    val preamble = c2s.readByte().toInt() and 0xFF
                    check(preamble == NOISE_PREAMBLE)
                    performNoiseHandshake(
                        input = c2s,
                        output = s2c,
                        psk = psk,
                        serverName = "superdash-test",
                        macAddress = "AA:BB:CC:DD:EE:FF",
                    )
                }

            // Drive an initiator using our same NoiseSymmetricState (proves
            // self-consistency of the responder pipeline).
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

            // split() returns (c1, c2) where:
            //   c1 = init->resp direction = initiator's sender
            //   c2 = resp->init direction = initiator's receiver
            val clientSplit = clientSym.split()
            val initToResp = clientSplit.first // c1: init->resp = initiator's sender
            val respToInit = clientSplit.second // c2: resp->init = initiator's receiver

            val serverResult = serverFut.await()
            val serverTransport =
                NoiseTransport(
                    input = c2s,
                    output = s2c,
                    sendCipher = serverResult.sendCipher,
                    recvCipher = serverResult.recvCipher,
                )

            // Client encrypts initiator->responder. Send a frame and decode at the server.
            val clientToServerPayload = byteArrayOf(0x0A, 0x02, 0x68, 0x69)
            val clientPlain =
                ByteArray(4 + clientToServerPayload.size).also { buf ->
                    buf[0] = (42 ushr 8 and 0xFF).toByte()
                    buf[1] = (42 and 0xFF).toByte()
                    buf[2] = (clientToServerPayload.size ushr 8 and 0xFF).toByte()
                    buf[3] = (clientToServerPayload.size and 0xFF).toByte()
                    System.arraycopy(clientToServerPayload, 0, buf, 4, clientToServerPayload.size)
                }
            val clientCt = initToResp.encryptWithAd(ByteArray(0), clientPlain)
            c2s.writeNoiseFrame(clientCt)

            val received = serverTransport.readFrame()
            assertEquals(42, received.messageType)
            assertArrayEquals(clientToServerPayload, received.payload)

            // Server replies via the transport.
            val replyPayload = byteArrayOf(7, 8)
            serverTransport.writeFrame(99, replyPayload)

            val replyWire = s2c.readNoiseFrame(maxPayloadLen = NOISE_MAX_PAYLOAD + 16)
            val replyPlain = respToInit.decryptWithAd(ByteArray(0), replyWire)
            val replyType =
                ((replyPlain[0].toInt() and 0xFF) shl 8) or (replyPlain[1].toInt() and 0xFF)
            val replySize =
                ((replyPlain[2].toInt() and 0xFF) shl 8) or (replyPlain[3].toInt() and 0xFF)
            assertEquals(99, replyType)
            assertEquals(2, replySize)
            assertArrayEquals(replyPayload, replyPlain.copyOfRange(4, 4 + replySize))

            c2s.close()
            s2c.close()
        }

    /** Regression test for the readNoiseFrame cap bug: a max-size payload
     *  (NOISE_MAX_PAYLOAD bytes) must survive a round-trip. Before the fix the
     *  cap was NOISE_MAX_PAYLOAD + macLength, which excludes the 4-byte inner
     *  header, causing the length check to reject the frame with an
     *  IllegalStateException. */
    @Test
    fun `transport round trips a max size payload`() =
        runTest(UnconfinedTestDispatcher()) {
            val c2s = ByteChannel(autoFlush = true)
            val s2c = ByteChannel(autoFlush = true)

            val serverFut =
                async {
                    val preamble = c2s.readByte().toInt() and 0xFF
                    check(preamble == NOISE_PREAMBLE)
                    performNoiseHandshake(
                        input = c2s,
                        output = s2c,
                        psk = psk,
                        serverName = "superdash-test",
                        macAddress = "AA:BB:CC:DD:EE:FF",
                    )
                }

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

            val serverResult = serverFut.await()
            val serverTransport =
                NoiseTransport(
                    input = c2s,
                    output = s2c,
                    sendCipher = serverResult.sendCipher,
                    recvCipher = serverResult.recvCipher,
                )

            // Send a frame with exactly NOISE_MAX_PAYLOAD bytes of payload.
            val maxPayload = ByteArray(NOISE_MAX_PAYLOAD) { (it and 0xFF).toByte() }
            val plain =
                ByteArray(4 + maxPayload.size).also { buf ->
                    buf[0] = (7 ushr 8 and 0xFF).toByte()
                    buf[1] = (7 and 0xFF).toByte()
                    buf[2] = (maxPayload.size ushr 8 and 0xFF).toByte()
                    buf[3] = (maxPayload.size and 0xFF).toByte()
                    System.arraycopy(maxPayload, 0, buf, 4, maxPayload.size)
                }
            val ciphertext = initToResp.encryptWithAd(ByteArray(0), plain)
            c2s.writeNoiseFrame(ciphertext)

            val received = serverTransport.readFrame()
            assertEquals(7, received.messageType)
            assertArrayEquals(maxPayload, received.payload)

            c2s.close()
            s2c.close()
        }
}
