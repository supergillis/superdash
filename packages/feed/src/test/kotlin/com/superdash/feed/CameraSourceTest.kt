package com.superdash.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraSourceTest {
    @Test fun `bare entity id parses as HaEntity`() {
        val source = parseCameraSource("camera.front_door")
        assertEquals(CameraSource.HaEntity("camera.front_door"), source)
    }

    @Test fun `http url parses as DirectUrl`() {
        val source = parseCameraSource("http://camera.example.com:1984/api/stream.mp4?src=main")
        assertEquals(CameraSource.DirectUrl("http://camera.example.com:1984/api/stream.mp4?src=main"), source)
    }

    @Test fun `https url parses as DirectUrl`() {
        val source = parseCameraSource("https://stream.example.com/live.m3u8")
        assertEquals(CameraSource.DirectUrl("https://stream.example.com/live.m3u8"), source)
    }

    @Test fun `rtsp url parses as DirectUrl`() {
        val source = parseCameraSource("rtsp://192.0.2.10:8554/front_door")
        assertEquals(CameraSource.DirectUrl("rtsp://192.0.2.10:8554/front_door"), source)
    }

    @Test fun `rtsps url parses as DirectUrl`() {
        val source = parseCameraSource("rtsps://camera.example.com:322/stream")
        assertEquals(CameraSource.DirectUrl("rtsps://camera.example.com:322/stream"), source)
    }

    // An entity_id can never start with "http" because HA forbids periods in
    // domains and "http" alone isn't a valid domain. So prefix-discrimination
    // is unambiguous in practice.
    @Test fun `entity-shaped value with no scheme parses as HaEntity`() {
        val source = parseCameraSource("camera.with_underscores")
        assertEquals(CameraSource.HaEntity("camera.with_underscores"), source)
    }

    @Test fun `empty string parses as HaEntity for backward compatibility`() {
        val source = parseCameraSource("")
        assertEquals(CameraSource.HaEntity(""), source)
    }
}
