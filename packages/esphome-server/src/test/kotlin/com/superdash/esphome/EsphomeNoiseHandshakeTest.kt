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
class EsphomeNoiseHandshakeTest {
    @Test
    fun `responder completes NNpsk0 handshake and post-handshake AEAD round trips`() =
        runTest(UnconfinedTestDispatcher()) {
            val psk = ByteArray(32) { (it + 1).toByte() }
            val serverName = "superdash-test"
            val macAddress = "AA:BB:CC:DD:EE:FF"

            // Two channels for full-duplex piping.
            val clientToServer = ByteChannel(autoFlush = true)
            val serverToClient = ByteChannel(autoFlush = true)

            val serverDeferred =
                async {
                    val preamble = clientToServer.readByte().toInt() and 0xFF
                    check(preamble == NOISE_PREAMBLE)
                    performNoiseHandshake(
                        input = clientToServer,
                        output = serverToClient,
                        psk = psk,
                        serverName = serverName,
                        macAddress = macAddress,
                    )
                }

            // --- Client side drives the NNpsk0 initiator manually. ---
            val clientSym = NoiseSymmetricState()
            clientSym.mixHash(NOISE_PROLOGUE)

            // Message 1: psk, e
            clientSym.mixKeyAndHash(psk)
            val ePriv = X25519.generatePrivateKey()
            val ePub = X25519.publicFromPrivate(ePriv)
            clientSym.mixHash(ePub)
            clientSym.mixKey(ePub) // PSK pattern
            val encryptedEmpty = clientSym.encryptAndHash(byteArrayOf())

            val msg1Body = ByteArray(1 + ePub.size + encryptedEmpty.size)
            msg1Body[0] = NOISE_HANDSHAKE_OK
            System.arraycopy(ePub, 0, msg1Body, 1, ePub.size)
            System.arraycopy(encryptedEmpty, 0, msg1Body, 1 + ePub.size, encryptedEmpty.size)
            clientToServer.writeNoiseFrame(msg1Body)

            // Message 2: read server's <- e, ee
            val msg2Body = serverToClient.readNoiseFrame(maxPayloadLen = 1024)
            assertEquals(NOISE_HANDSHAKE_OK, msg2Body[0])
            val serverEPub = msg2Body.copyOfRange(1, 1 + 32)
            val encryptedName = msg2Body.copyOfRange(1 + 32, msg2Body.size)

            clientSym.mixHash(serverEPub)
            clientSym.mixKey(serverEPub) // PSK pattern
            val shared = X25519.computeSharedSecret(ePriv, serverEPub)
            clientSym.mixKey(shared)
            val serverHello = clientSym.decryptAndHash(encryptedName)

            // aioesphomeapi format: 0x01 | <name UTF-8> 0x00 | <mac ASCII> 0x00
            assertEquals(0x01.toByte(), serverHello[0])
            val i1 = (1 until serverHello.size).first { serverHello[it] == 0x00.toByte() }
            val parsedName = serverHello.copyOfRange(1, i1).toString(Charsets.UTF_8)
            assertEquals(serverName, parsedName)
            val i2 = (i1 + 1 until serverHello.size).first { serverHello[it] == 0x00.toByte() }
            val parsedMac = serverHello.copyOfRange(i1 + 1, i2).toString(Charsets.UTF_8)
            assertEquals(macAddress, parsedMac)

            val serverResult = serverDeferred.await()

            // Internal consistency: both sides agree on the transport hash.
            assertArrayEquals(clientSym.handshakeHash, serverResult.handshakeHash)

            // Split() on the client. c1 = initiator -> responder; c2 = responder -> initiator.
            val (clientSend, clientRecv) = clientSym.split()

            // Round trip client -> server.
            val payloadOut = "hello server".toByteArray(Charsets.UTF_8)
            val ct1 = clientSend.encryptWithAd(byteArrayOf(), payloadOut)
            val pt1 = serverResult.recvCipher.decryptWithAd(byteArrayOf(), ct1)
            assertArrayEquals(payloadOut, pt1)

            // Round trip server -> client.
            val payloadIn = "hello client".toByteArray(Charsets.UTF_8)
            val ct2 = serverResult.sendCipher.encryptWithAd(byteArrayOf(), payloadIn)
            val pt2 = clientRecv.decryptWithAd(byteArrayOf(), ct2)
            assertArrayEquals(payloadIn, pt2)

            clientToServer.close()
            serverToClient.close()
        }
}
