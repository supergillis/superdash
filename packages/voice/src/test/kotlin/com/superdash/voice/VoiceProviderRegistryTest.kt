package com.superdash.voice

import com.superdash.voice.pipeline.ResolvedVoiceProvider
import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceProviderRegistryTest {
    @Test
    fun `moonshine identity includes selected model id`() {
        val registry =
            VoiceProviderRegistry(
                providerFactory = { identity ->
                    ResolvedVoiceProvider(identity = identity, provider = { _ -> flow { } })
                },
            )

        val provider =
            registry.resolve(
                VoiceProviderIdentity(providerKey = "moonshine", modelId = "moonshine-base-en"),
            )

        assertNotNull(provider)
        assertEquals("moonshine:moonshine-base-en", provider!!.identity.stableKey)
    }

    @Test
    fun `resolved providers are cached by stable identity`() {
        var createCount = 0
        val registry =
            VoiceProviderRegistry(
                providerFactory = { identity ->
                    createCount += 1
                    ResolvedVoiceProvider(identity = identity, provider = { _ -> flow { } })
                },
            )
        val identity = VoiceProviderIdentity(providerKey = "moonshine", modelId = "moonshine-base-en")

        val first = registry.resolve(identity)
        val second = registry.resolve(identity)

        assertSame(first, second)
        assertEquals(1, createCount)
    }

    @Test
    fun `evict closes the previously resolved provider and removes it from the cache`() =
        runTest {
            var closeCalls = 0
            val registry =
                VoiceProviderRegistry(
                    providerFactory = { identity ->
                        ResolvedVoiceProvider(
                            identity = identity,
                            provider = { _ -> flow { } },
                            closeable = { closeCalls += 1 },
                        )
                    },
                )

            val first = registry.resolve(VoiceProviderIdentity("moonshine", "tiny-en"))
            assertEquals(0, closeCalls)

            registry.evict("moonshine")
            assertEquals(1, closeCalls)

            val rebuilt = registry.resolve(VoiceProviderIdentity("moonshine", "tiny-en"))
            assertEquals(1, closeCalls)
            assertNotSame(first, rebuilt)
        }

    @Test
    fun `evict only closes entries whose providerKey matches`() =
        runTest {
            var moonshineClose = 0
            var whisperClose = 0
            val registry =
                VoiceProviderRegistry(
                    providerFactory = { identity ->
                        ResolvedVoiceProvider(
                            identity = identity,
                            provider = { _ -> flow { } },
                            closeable = {
                                when (identity.providerKey) {
                                    "moonshine" -> moonshineClose += 1
                                    "whisper" -> whisperClose += 1
                                }
                            },
                        )
                    },
                )

            registry.resolve(VoiceProviderIdentity("moonshine", "tiny-en"))
            registry.resolve(VoiceProviderIdentity("whisper", null))

            registry.evict("moonshine")
            assertEquals(1, moonshineClose)
            assertEquals(0, whisperClose)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `evict does not block the dispatcher while engine close awaits in-flight work`() =
        runTest {
            // Simulates an in-flight transcription holding the engine mutex.
            // The close path must suspend (not block) so other coroutines on
            // the same dispatcher can make progress while close awaits the mutex.
            val inflightMutex = Mutex()
            val transcriptionStarted = CompletableDeferred<Unit>()
            val releaseTranscription = CompletableDeferred<Unit>()

            val registry =
                VoiceProviderRegistry(
                    providerFactory = { identity ->
                        ResolvedVoiceProvider(
                            identity = identity,
                            provider = { _ -> flow { } },
                            closeable = {
                                // Mirrors BatchLocalSttEngine.close(): suspend on the
                                // in-flight mutex so we await any active transcription.
                                inflightMutex.withLock { /* released */ }
                            },
                        )
                    },
                )
            registry.resolve(VoiceProviderIdentity("moonshine", "tiny-en"))

            // Start a "transcription" that holds the mutex until released.
            val transcription =
                launch {
                    inflightMutex.withLock {
                        transcriptionStarted.complete(Unit)
                        releaseTranscription.await()
                    }
                }
            transcriptionStarted.await()

            // Kick off evict while transcription holds the mutex; evict must suspend.
            val evictJob = async { registry.evict("moonshine") }

            // A bystander coroutine on the same test dispatcher must still progress.
            // If evict blocked the dispatcher (e.g. via runBlocking), this would never run
            // before we release the transcription.
            val bystanderRan = CompletableDeferred<Unit>()
            launch {
                yield()
                bystanderRan.complete(Unit)
            }

            advanceUntilIdle()
            assertTrue(
                "bystander coroutine should progress while evict awaits in-flight work",
                bystanderRan.isCompleted,
            )
            assertFalse(
                "evict should still be suspended while transcription holds the mutex",
                evictJob.isCompleted,
            )

            // Release the transcription; evict should now complete.
            releaseTranscription.complete(Unit)
            evictJob.await()
            transcription.join()
        }
}
