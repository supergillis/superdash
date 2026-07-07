package com.superdash.voice

import com.superdash.ha.EntityState
import com.superdash.voice.intent.LocalIntentAction
import com.superdash.voice.intent.LocalIntentRegistryRecognizer
import com.superdash.voice.intent.LocalIntentStatus
import com.superdash.voice.intent.registry.LocalIntentRegistry
import com.superdash.voice.intent.registry.LocalIntentRegistryMetadata
import com.superdash.voice.intent.registry.LocalIntentRegistrySnapshot
import com.superdash.voice.intent.registry.buildLocalIntentRegistry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocalIntentRegistryRecognizerTest {
    @Test fun `generated registry match returns service action`() =
        runTest {
            val recognizer =
                LocalIntentRegistryRecognizer(
                    registryProvider = {
                        LocalIntentRegistrySnapshot(
                            registry =
                                buildLocalIntentRegistry(
                                    entities =
                                        mapOf(
                                            "light.office" to entity("light.office", "Office Lights"),
                                        ),
                                    metadata = metadata(setOf("light.office")),
                                ),
                            available = true,
                        )
                    },
                )

            val result = recognizer.recognize("turn on office lights")

            assertEquals(LocalIntentStatus.Matched, result.status)
            assertEquals("turn_on", result.intentId)
            assertEquals("turn on office lights", result.transcript)
            val serviceCall = result.action as? LocalIntentAction.ServiceCall
            assertEquals("light", serviceCall?.call?.domain)
            assertEquals("turn_on", serviceCall?.call?.service)
            assertNotNull(serviceCall?.call?.target)
        }

    @Test fun `ambiguous generated registry match returns ambiguous status`() =
        runTest {
            val recognizer =
                LocalIntentRegistryRecognizer(
                    registryProvider = {
                        LocalIntentRegistrySnapshot(
                            registry =
                                buildLocalIntentRegistry(
                                    entities =
                                        mapOf(
                                            "light.office_main" to entity("light.office_main", "Office"),
                                            "light.office_lamp" to entity("light.office_lamp", "Office"),
                                        ),
                                    metadata = metadata(setOf("light.office_main", "light.office_lamp")),
                                ),
                            available = true,
                        )
                    },
                )

            val result = recognizer.recognize("turn on office")

            assertEquals(LocalIntentStatus.AmbiguousPhrase, result.status)
            assertEquals("turn on office", result.transcript)
        }

    @Test fun `unavailable registry returns stale registry status`() =
        runTest {
            val recognizer =
                LocalIntentRegistryRecognizer(
                    registryProvider = {
                        LocalIntentRegistrySnapshot(
                            registry = LocalIntentRegistry(emptyList()),
                            available = false,
                        )
                    },
                )

            val result = recognizer.recognize("turn on office lights")

            assertEquals(LocalIntentStatus.StaleRegistry, result.status)
            assertEquals("turn on office lights", result.transcript)
        }

    private fun entity(
        entityId: String,
        friendlyName: String,
    ): EntityState =
        EntityState(
            entityId = entityId,
            state = "off",
            attributes = JsonObject(mapOf("friendly_name" to JsonPrimitive(friendlyName))),
        )

    private fun metadata(exposedEntityIds: Set<String>): LocalIntentRegistryMetadata =
        LocalIntentRegistryMetadata(
            entityRegistry = emptyMap(),
            deviceRegistry = emptyMap(),
            exposedEntityIds = exposedEntityIds,
            loaded = true,
        )
}
