package com.superdash.esphome

import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.esphome.api.CameraImageRequest
import org.esphome.api.CameraImageResponse
import org.esphome.api.HelloRequest
import org.esphome.api.ListEntitiesBinarySensorResponse
import org.esphome.api.ListEntitiesCameraResponse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EsphomeCameraConnectionTest {
    private class Harness(
        scope: TestScope,
        entities: List<EsphomeEntity>,
        nanoTime: () -> Long,
    ) {
        val clientToServer = ByteChannel(autoFlush = true)
        val serverToClient = ByteChannel(autoFlush = true)
        val connection =
            EsphomeConnection(
                transport = PlainTransport(input = clientToServer, output = serverToClient),
                deviceInfo =
                    EsphomeDeviceInfo(
                        name = "superdash-test",
                        macAddress = "AA:BB:CC:DD:EE:FF",
                        esphomeVersion = "2026.4.5-superdash",
                        compilationTime = "",
                        model = "Pixel emulator",
                        manufacturer = "Google",
                        friendlyName = "Superdash Test",
                    ),
                entities = entities,
                nanoTime = nanoTime,
            )
        val job = scope.launch { connection.run() }

        suspend fun hello() {
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.HELLO_REQUEST,
                HelloRequest
                    .newBuilder()
                    .setClientInfo("test")
                    .build()
                    .toByteArray(),
            )
            serverToClient.readEsphomeFrame() // HelloResponse
        }

        suspend fun requestImage(
            single: Boolean,
            stream: Boolean,
        ) {
            clientToServer.writeEsphomeFrame(
                EsphomeMessageType.CAMERA_IMAGE_REQUEST,
                CameraImageRequest
                    .newBuilder()
                    .setSingle(single)
                    .setStream(stream)
                    .build()
                    .toByteArray(),
            )
        }

        /** Reads CameraImageResponse chunks until done and reassembles. */
        suspend fun readImage(): ByteArray {
            val bytes = ArrayList<Byte>()
            while (true) {
                val frame = serverToClient.readEsphomeFrame()
                assertEquals(EsphomeMessageType.CAMERA_IMAGE_RESPONSE, frame.messageType)
                val chunk = CameraImageResponse.parseFrom(frame.payload)
                bytes.addAll(chunk.data.toByteArray().toList())
                if (chunk.done) {
                    return bytes.toByteArray()
                }
            }
        }
    }

    private fun cameraEntity(
        frames: MutableSharedFlow<ByteArray>,
        latest: ByteArray?,
    ): EsphomeEntity.Camera =
        EsphomeEntity.Camera(
            key = keyFromObjectId("camera"),
            objectId = "camera",
            name = "Camera",
            frames = frames,
            latestJpeg = { latest },
        )

    @Test
    fun `list entities includes camera and binary sensor device class`() =
        runTest(UnconfinedTestDispatcher()) {
            val frames = MutableSharedFlow<ByteArray>()
            val entities =
                listOf(
                    EsphomeEntity.BinarySensor(
                        key = keyFromObjectId("motion"),
                        objectId = "motion",
                        name = "Motion",
                        state = MutableSharedFlow(),
                        deviceClass = "motion",
                    ),
                    cameraEntity(frames, latest = null),
                )
            val harness = Harness(this, entities, nanoTime = { 0L })
            harness.hello()
            harness.clientToServer.writeEsphomeFrame(EsphomeMessageType.LIST_ENTITIES_REQUEST, ByteArray(0))

            val binary = harness.serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_BINARY_SENSOR_RESPONSE, binary.messageType)
            assertEquals("motion", ListEntitiesBinarySensorResponse.parseFrom(binary.payload).deviceClass)

            val camera = harness.serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_CAMERA_RESPONSE, camera.messageType)
            val parsed = ListEntitiesCameraResponse.parseFrom(camera.payload)
            assertEquals("camera", parsed.objectId)
            assertEquals("Camera", parsed.name)

            val done = harness.serverToClient.readEsphomeFrame()
            assertEquals(EsphomeMessageType.LIST_ENTITIES_DONE_RESPONSE, done.messageType)
            harness.job.cancel()
        }

    @Test
    fun `single request sends the latest jpeg chunked`() =
        runTest(UnconfinedTestDispatcher()) {
            val jpeg = ByteArray(40_000) { it.toByte() } // > 2 chunks at 15 KiB
            val harness =
                Harness(this, listOf(cameraEntity(MutableSharedFlow(), latest = jpeg)), nanoTime = { 0L })
            harness.hello()
            harness.requestImage(single = true, stream = false)
            assertArrayEquals(jpeg, harness.readImage())
            harness.job.cancel()
        }

    @Test
    fun `stream request pushes frames until the window closes`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            val frames = MutableSharedFlow<ByteArray>()
            val harness = Harness(this, listOf(cameraEntity(frames, latest = null)), nanoTime = { now })
            harness.hello()
            harness.requestImage(single = false, stream = true)

            frames.emit(byteArrayOf(1, 2, 3))
            assertArrayEquals(byteArrayOf(1, 2, 3), harness.readImage())

            // Past the 5s window: the next frame ends the stream job, nothing is sent.
            now = 6_000_000_000L
            frames.emit(byteArrayOf(4, 5, 6))
            assertEquals(0, frames.subscriptionCount.value)
            harness.job.cancel()
        }

    @Test
    fun `stream request refresh extends the window`() =
        runTest(UnconfinedTestDispatcher()) {
            var now = 0L
            val frames = MutableSharedFlow<ByteArray>()
            val harness = Harness(this, listOf(cameraEntity(frames, latest = null)), nanoTime = { now })
            harness.hello()
            harness.requestImage(single = false, stream = true)
            frames.emit(byteArrayOf(1))
            harness.readImage()

            now = 4_000_000_000L
            harness.requestImage(single = false, stream = true) // refresh at t=4s → window to t=9s
            now = 8_000_000_000L
            frames.emit(byteArrayOf(2))
            assertArrayEquals(byteArrayOf(2), harness.readImage())
            harness.job.cancel()
        }

    @Test
    fun `single request with no cached frame sends nothing and keeps connection alive`() =
        runTest(UnconfinedTestDispatcher()) {
            val harness =
                Harness(this, listOf(cameraEntity(MutableSharedFlow(), latest = null)), nanoTime = { 0L })
            harness.hello()
            harness.requestImage(single = true, stream = false)
            // Connection still answers pings afterwards.
            harness.clientToServer.writeEsphomeFrame(EsphomeMessageType.PING_REQUEST, ByteArray(0))
            assertEquals(EsphomeMessageType.PING_RESPONSE, harness.serverToClient.readEsphomeFrame().messageType)
            assertTrue(harness.job.isActive)
            harness.job.cancel()
        }
}
