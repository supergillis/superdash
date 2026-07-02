package com.superdash.voice

import com.superdash.voice.action.VoiceActionEvent
import com.superdash.voice.action.VoiceActionProvider
import com.superdash.voice.action.recognizedWordsFromText
import com.superdash.voice.pipeline.LocalSttRoute
import com.superdash.voice.pipeline.ResolvedVoiceProvider
import com.superdash.voice.pipeline.VoiceProviderAttempt
import com.superdash.voice.pipeline.VoiceProviderAttemptResult
import com.superdash.voice.pipeline.VoiceProviderIdentity
import com.superdash.voice.pipeline.VoiceProviderProvenance
import com.superdash.voice.pipeline.VoiceProviderRegistry
import com.superdash.voice.pipeline.VoiceProviderRun
import com.superdash.voice.pipeline.VoiceProviderRunEvent
import com.superdash.voice.pipeline.VoiceProviderSelection
import com.superdash.voice.pipeline.VoiceProviderWithFallback
import com.superdash.voice.stt.RecognitionUpdate
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceProviderWithFallbackTest {
    @Test
    fun `uses primary provider when primary completes action`() =
        runTest {
            val primary = VoiceProviderIdentity("primary", null)
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        if (identity == primary) {
                            actionProvider("primary")
                        } else {
                            null
                        }
                    },
                )

            val result = provider.collectRun(VoiceProviderSelection(primary, null), twoFrameAudio())

            assertEquals("primary", (result.events.single() as VoiceActionEvent.ActionComplete).transcript)
        }

    @Test
    fun `uses secondary provider with full audio when primary fails`() =
        runTest {
            val primary = VoiceProviderIdentity("primary", null)
            val secondary = VoiceProviderIdentity("secondary", null)
            val seenBySecondary = mutableListOf<ShortArray>()
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        when (identity) {
                            primary -> {
                                { audio ->
                                    flow {
                                        audio.toList().first()
                                        emit(VoiceActionEvent.Error("primary-failed", "failed"))
                                    }
                                }
                            }
                            secondary -> {
                                { audio ->
                                    flow {
                                        audio.collect { seenBySecondary += it.copyOf() }
                                        emit(VoiceActionEvent.ActionComplete("secondary", buildJsonObject {}))
                                    }
                                }
                            }
                            else -> {
                                null
                            }
                        }
                    },
                )

            val result = provider.collectRun(VoiceProviderSelection(primary, secondary), twoFrameAudio())

            assertEquals(1, result.events.size)
            assertEquals("secondary", (result.events.last() as VoiceActionEvent.ActionComplete).transcript)
            assertEquals(2, seenBySecondary.size)
            assertEquals(1, seenBySecondary[0][0].toInt())
            assertEquals(2, seenBySecondary[1][0].toInt())
        }

    @Test
    fun `uses secondary provider with full audio when primary fails before consuming all audio`() =
        runTest {
            val primary = VoiceProviderIdentity("primary", null)
            val secondary = VoiceProviderIdentity("secondary", null)
            val seenBySecondary = mutableListOf<ShortArray>()
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        when (identity) {
                            primary -> {
                                { audio ->
                                    flow {
                                        audio.first()
                                        emit(VoiceActionEvent.Error("primary-failed", "failed"))
                                    }
                                }
                            }
                            secondary -> {
                                { audio ->
                                    flow {
                                        audio.collect { frame -> seenBySecondary += frame.copyOf() }
                                        emit(VoiceActionEvent.ActionComplete("secondary", buildJsonObject {}))
                                    }
                                }
                            }
                            else -> {
                                null
                            }
                        }
                    },
                )

            val result =
                provider.collectRun(
                    VoiceProviderSelection(primary, secondary),
                    flowOf(shortArrayOf(1), shortArrayOf(2), shortArrayOf(3)),
                )

            assertEquals("secondary", (result.events.last() as VoiceActionEvent.ActionComplete).transcript)
            assertEquals(3, seenBySecondary.size)
            assertEquals(1, seenBySecondary[0][0].toInt())
            assertEquals(2, seenBySecondary[1][0].toInt())
            assertEquals(3, seenBySecondary[2][0].toInt())
        }

    @Test
    fun `does not use secondary provider when primary completes then emits error`() =
        runTest {
            val primary = VoiceProviderIdentity("primary", null)
            val secondary = VoiceProviderIdentity("secondary", null)
            var secondaryUsed = false
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        when (identity) {
                            primary -> {
                                { audio ->
                                    flow {
                                        audio.collect { }
                                        emit(VoiceActionEvent.ActionComplete("primary", buildJsonObject {}))
                                        emit(VoiceActionEvent.Error("primary-error", "late error"))
                                    }
                                }
                            }
                            secondary -> {
                                {
                                    secondaryUsed = true
                                    actionProvider("secondary")(it)
                                }
                            }
                            else -> {
                                null
                            }
                        }
                    },
                )

            val result = provider.collectRun(VoiceProviderSelection(primary, secondary), twoFrameAudio())

            assertEquals("primary", (result.events.single() as VoiceActionEvent.ActionComplete).transcript)
            assertFalse(secondaryUsed)
        }

    @Test
    fun `does not create unused secondary provider when primary succeeds`() =
        runTest {
            val primary = VoiceProviderIdentity("primary", null)
            val secondary = VoiceProviderIdentity("secondary", null)
            var secondaryCreated = false
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        when (identity) {
                            primary -> {
                                actionProvider("primary")
                            }
                            secondary -> {
                                secondaryCreated = true
                                actionProvider("secondary")
                            }
                            else -> {
                                null
                            }
                        }
                    },
                )

            provider.collectRun(VoiceProviderSelection(primary, secondary), twoFrameAudio())

            assertFalse(secondaryCreated)
        }

    @Test
    fun `reuses provider instance across voice runs`() =
        runTest {
            val primary = VoiceProviderIdentity("primary", null)
            var primaryCreated = 0
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        if (identity == primary) {
                            primaryCreated += 1
                            actionProvider("primary")
                        } else {
                            null
                        }
                    },
                )

            provider.collectRun(VoiceProviderSelection(primary, null), twoFrameAudio())
            provider.collectRun(VoiceProviderSelection(primary, null), twoFrameAudio())

            assertEquals(1, primaryCreated)
        }

    @Test
    fun `streams primary audio without buffering when secondary is none`() =
        runTest {
            val primary = VoiceProviderIdentity("primary", null)
            val seenByPrimary = mutableListOf<ShortArray>()
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        if (identity == primary) {
                            { audio ->
                                flow {
                                    audio.collect {
                                        seenByPrimary += it.copyOf()
                                    }
                                    emit(VoiceActionEvent.ActionComplete("primary", buildJsonObject {}))
                                }
                            }
                        } else {
                            null
                        }
                    },
                )

            provider.collectRun(VoiceProviderSelection(primary, null), twoFrameAudio())

            assertEquals(2, seenByPrimary.size)
            assertEquals(1, seenByPrimary[0][0].toInt())
            assertEquals(2, seenByPrimary[1][0].toInt())
        }

    @Test
    fun `creates separate cached providers for model specific keys`() =
        runTest {
            var firstModelCreates = 0
            var secondModelCreates = 0
            val first = VoiceProviderIdentity("moonshine", "first")
            val second = VoiceProviderIdentity("moonshine", "second")
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        when (identity) {
                            first -> {
                                firstModelCreates += 1
                                actionProvider("first")
                            }
                            second -> {
                                secondModelCreates += 1
                                actionProvider("second")
                            }
                            else -> {
                                null
                            }
                        }
                    },
                )

            provider.collectRun(VoiceProviderSelection(first, null), twoFrameAudio())
            provider.collectRun(VoiceProviderSelection(second, null), twoFrameAudio())
            provider.collectRun(VoiceProviderSelection(first, null), twoFrameAudio())

            assertEquals(1, firstModelCreates)
            assertEquals(1, secondModelCreates)
        }

    @Test
    fun `fallback records primary failure and secondary success`() =
        runTest {
            val primary = VoiceProviderIdentity("moonshine", "moonshine-tiny-en")
            val secondary = VoiceProviderIdentity("ha_assist", null)
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        when (identity.providerKey) {
                            "moonshine" -> {
                                { audio ->
                                    flow {
                                        audio.collect { }
                                        emit(VoiceActionEvent.Error("local", "failed"))
                                    }
                                }
                            }
                            "ha_assist" -> {
                                { audio ->
                                    flow {
                                        audio.collect { }
                                        emit(VoiceActionEvent.ActionComplete("ok", buildJsonObject {}))
                                    }
                                }
                            }
                            else -> {
                                null
                            }
                        }
                    },
                )

            val result = provider.collectRun(VoiceProviderSelection(primary, secondary), shortFramesFlow())

            assertEquals(2, result.providerTrace.size)
            assertEquals("moonshine:moonshine-tiny-en", result.providerTrace[0].identity.stableKey)
            assertEquals("ha_assist", result.providerTrace[1].identity.stableKey)
            assertTrue(result.providerTrace[0].result is VoiceProviderAttemptResult.Failed)
            assertEquals(
                VoiceProviderAttemptResult.Completed(actionComplete = true),
                result.providerTrace[1].result,
            )
        }

    @Test
    fun `local stt provenance is recorded in provider trace and removed from events`() =
        runTest {
            val primary = VoiceProviderIdentity("moonshine", "moonshine-tiny-en")
            val provider =
                fallbackProvider(
                    providerFactory = {
                        { _ ->
                            flowOf(
                                VoiceActionEvent.ProviderProvenance(
                                    VoiceProviderProvenance.LocalStt(
                                        route = LocalSttRoute.HaText,
                                        transcript = "turn on desk lights",
                                        reason = null,
                                    ),
                                ),
                                VoiceActionEvent.ActionComplete("turn on desk lights", buildJsonObject {}),
                            )
                        }
                    },
                )

            val result = provider.collectRun(VoiceProviderSelection(primary, null), shortFramesFlow())

            assertEquals(listOf(VoiceActionEvent.ActionComplete("turn on desk lights", buildJsonObject {})), result.events)
            assertEquals(
                listOf(
                    VoiceProviderProvenance.LocalStt(
                        route = LocalSttRoute.HaText,
                        transcript = "turn on desk lights",
                        reason = null,
                    ),
                ),
                result.providerTrace.single().provenance,
            )
        }

    @Test
    fun `fallback replays command audio to secondary provider`() =
        runTest {
            val primary = VoiceProviderIdentity("moonshine", "moonshine-tiny-en")
            val secondary = VoiceProviderIdentity("ha_assist", null)
            val observedSamples = mutableMapOf<String, List<Int>>()
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        { audio ->
                            flow {
                                observedSamples[identity.providerKey] =
                                    audio
                                        .toList()
                                        .flatMap { frame -> frame.map { sample -> sample.toInt() } }
                                if (identity.providerKey == "moonshine") {
                                    emit(VoiceActionEvent.Error("local", "failed"))
                                } else {
                                    emit(VoiceActionEvent.ActionComplete("ok", buildJsonObject {}))
                                }
                            }
                        }
                    },
                )

            provider.collectRun(VoiceProviderSelection(primary, secondary), flowOf(shortArrayOf(1, 2), shortArrayOf(3)))

            assertEquals(listOf(1, 2, 3), observedSamples["moonshine"])
            assertEquals(listOf(1, 2, 3), observedSamples["ha_assist"])
        }

    @Test
    fun `fallback configuration streams primary non error events before audio completes`() =
        runTest {
            val primary = VoiceProviderIdentity("moonshine", "moonshine-tiny-en")
            val secondary = VoiceProviderIdentity("ha_assist", null)
            val provider =
                fallbackProvider(
                    providerFactory = { identity ->
                        when (identity) {
                            primary -> {
                                { audio ->
                                    flow {
                                        audio.first()
                                        emit(
                                            VoiceActionEvent.Recognition(
                                                RecognitionUpdate.Final(words = recognizedWordsFromText("turn on desk")),
                                            ),
                                        )
                                        awaitCancellation()
                                    }
                                }
                            }
                            secondary -> {
                                actionProvider("secondary")
                            }
                            else -> {
                                null
                            }
                        }
                    },
                )

            val firstEvent =
                provider
                    .run(VoiceProviderSelection(primary, secondary), neverEndingAudio())
                    .take(1)
                    .toList()
                    .single()

            assertEquals(
                VoiceProviderRunEvent.Action(
                    VoiceActionEvent.Recognition(
                        RecognitionUpdate.Final(words = recognizedWordsFromText("turn on desk")),
                    ),
                ),
                firstEvent,
            )
        }

    private fun actionProvider(transcript: String): VoiceActionProvider =
        { flowOf(VoiceActionEvent.ActionComplete(transcript, buildJsonObject {})) }

    private fun fallbackProvider(providerFactory: (VoiceProviderIdentity) -> VoiceActionProvider?): VoiceProviderWithFallback =
        VoiceProviderWithFallback(
            VoiceProviderRegistry { identity ->
                val provider = providerFactory(identity)
                if (provider == null) {
                    null
                } else {
                    ResolvedVoiceProvider(identity = identity, provider = provider)
                }
            },
        )

    private suspend fun VoiceProviderWithFallback.collectRun(
        selection: VoiceProviderSelection,
        audio: Flow<ShortArray>,
    ): VoiceProviderRun {
        val events = mutableListOf<VoiceActionEvent>()
        val attempts = mutableListOf<VoiceProviderAttempt>()
        run(selection, audio).collect { event ->
            when (event) {
                is VoiceProviderRunEvent.Action -> {
                    events += event.event
                }
                is VoiceProviderRunEvent.AttemptFinished -> {
                    attempts += event.attempt
                }
            }
        }
        return VoiceProviderRun(events = events, providerTrace = attempts)
    }

    private fun twoFrameAudio(): Flow<ShortArray> =
        flowOf(shortArrayOf(1), shortArrayOf(2))

    private fun shortFramesFlow(): Flow<ShortArray> =
        flow {
            emit(shortArrayOf(1, 2, 3))
        }

    private fun neverEndingAudio(): Flow<ShortArray> =
        flow {
            emit(shortArrayOf(1, 2, 3))
            awaitCancellation()
        }
}
