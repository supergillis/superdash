package com.superdash.doorbell

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DoorbellStreamResolverTest {
    private fun config(cameraEntity: String) =
        DoorbellConfig(
            id = "a",
            name = "Front",
            triggerEntity = "binary_sensor.front",
            cameraEntity = cameraEntity,
        )

    @Test
    fun `direct URL camera source returns Ready with no bearer token`() =
        runTest {
            var fetchCalled = false
            var bearerCalled = false
            val result =
                resolveDoorbellStream(
                    config = config("https://cam.local/stream"),
                    haBaseUrl = "https://ha.local",
                    fetchHlsUrl = {
                        fetchCalled = true
                        "/unused"
                    },
                    bearerTokenProvider = {
                        bearerCalled = true
                        "unused"
                    },
                )
            assertEquals(
                DoorbellStreamState.Ready(
                    streamUrl = "https://cam.local/stream",
                    bearerToken = null,
                ),
                result,
            )
            assertTrue(!fetchCalled)
            assertTrue(!bearerCalled)
        }

    @Test
    fun `HA entity camera source returns Ready with absolute URL and bearer token`() =
        runTest {
            val result =
                resolveDoorbellStream(
                    config = config("camera.front_door"),
                    haBaseUrl = "https://ha.local",
                    fetchHlsUrl = { entity ->
                        assertEquals("camera.front_door", entity)
                        "/api/hls/abc/playlist.m3u8"
                    },
                    bearerTokenProvider = { "TOKEN" },
                )
            assertEquals(
                DoorbellStreamState.Ready(
                    streamUrl = "https://ha.local/api/hls/abc/playlist.m3u8",
                    bearerToken = "TOKEN",
                ),
                result,
            )
        }

    @Test
    fun `trailing slash on haBaseUrl is trimmed before concatenation`() =
        runTest {
            val result =
                resolveDoorbellStream(
                    config = config("camera.front_door"),
                    haBaseUrl = "https://ha.local/",
                    fetchHlsUrl = { "/api/hls/x/playlist.m3u8" },
                    bearerTokenProvider = { "T" },
                )
            assertTrue(result is DoorbellStreamState.Ready)
            assertEquals(
                "https://ha.local/api/hls/x/playlist.m3u8",
                (result as DoorbellStreamState.Ready).streamUrl,
            )
        }

    @Test
    fun `HA entity with null bearer token still returns Ready`() =
        runTest {
            val result =
                resolveDoorbellStream(
                    config = config("camera.front_door"),
                    haBaseUrl = "https://ha.local",
                    fetchHlsUrl = { "/api/hls/x/playlist.m3u8" },
                    bearerTokenProvider = { null },
                )
            assertTrue(result is DoorbellStreamState.Ready)
            assertNull((result as DoorbellStreamState.Ready).bearerToken)
        }

    @Test
    fun `fetchHlsUrl throwing returns Failed with the thrown message`() =
        runTest {
            val result =
                resolveDoorbellStream(
                    config = config("camera.front_door"),
                    haBaseUrl = "https://ha.local",
                    fetchHlsUrl = { throw RuntimeException("boom") },
                    bearerTokenProvider = { "T" },
                )
            assertEquals(DoorbellStreamState.Failed("boom"), result)
        }

    @Test
    fun `fetchHlsUrl throwing with null message returns Failed with default message`() =
        runTest {
            val result =
                resolveDoorbellStream(
                    config = config("camera.front_door"),
                    haBaseUrl = "https://ha.local",
                    fetchHlsUrl = { throw RuntimeException() },
                    bearerTokenProvider = { "T" },
                )
            assertEquals(DoorbellStreamState.Failed(null), result)
        }

    @Test
    fun `CancellationException from fetchHlsUrl is re-thrown instead of becoming Failed`() =
        runTest {
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    resolveDoorbellStream(
                        config = config("camera.front_door"),
                        haBaseUrl = "https://ha.local",
                        fetchHlsUrl = { throw CancellationException("parent cancelled") },
                        bearerTokenProvider = { "T" },
                    )
                }
            }
        }

    @Test
    fun `CancellationException from bearerTokenProvider is re-thrown instead of becoming Failed`() =
        runTest {
            assertThrows(CancellationException::class.java) {
                runBlocking {
                    resolveDoorbellStream(
                        config = config("camera.front_door"),
                        haBaseUrl = "https://ha.local",
                        fetchHlsUrl = { "/api/hls/x/playlist.m3u8" },
                        bearerTokenProvider = { throw CancellationException("parent cancelled") },
                    )
                }
            }
        }
}
