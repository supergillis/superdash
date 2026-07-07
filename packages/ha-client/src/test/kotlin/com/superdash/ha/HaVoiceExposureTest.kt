package com.superdash.ha

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class HaVoiceExposureTest {
    private val json = haJson

    @Test fun `extracts conversation exposure map keys`() {
        val payload =
            json
                .parseToJsonElement(
                    """
                    {
                      "exposed_entities": {
                        "light.office": {
                          "conversation": true
                        },
                        "switch.desk": {
                          "conversation": true,
                          "cloud.alexa": false
                        },
                        "light.secret": {
                          "conversation": false
                        }
                      }
                    }
                    """.trimIndent(),
                ).jsonObject

        assertEquals(
            HaVoiceExposureSnapshot(
                exposedEntityIds = setOf("light.office", "switch.desk"),
                loaded = true,
            ),
            extractConversationExposure(payload),
        )
    }

    @Test fun `excludes explicit false exposure values`() {
        val payload =
            json
                .parseToJsonElement(
                    """
                    {
                      "exposed_entities": {
                        "light.office": {
                          "conversation": true
                        },
                        "switch.hidden": {
                          "conversation": false
                        },
                        "switch.desk": {
                          "conversation": true
                        }
                      }
                    }
                    """.trimIndent(),
                ).jsonObject

        assertEquals(
            HaVoiceExposureSnapshot(
                exposedEntityIds = setOf("light.office", "switch.desk"),
                loaded = true,
            ),
            extractConversationExposure(payload),
        )
    }

    @Test fun `extracts legacy assistant keyed exposure map`() {
        val payload =
            json
                .parseToJsonElement(
                    """
                    {
                      "exposed_entities": {
                        "conversation": {
                          "light.office": {},
                          "switch.desk": {}
                        }
                      }
                    }
                    """.trimIndent(),
                ).jsonObject

        assertEquals(
            HaVoiceExposureSnapshot(
                exposedEntityIds = setOf("light.office", "switch.desk"),
                loaded = true,
            ),
            extractConversationExposure(payload),
        )
    }

    @Test fun `extracts conversation exposure from entity registry options`() {
        val entries =
            listOf(
                HaEntityRegistryEntry(
                    entityId = "light.office",
                    options = HaEntityRegistryOptions(conversation = HaConversationEntityOptions(shouldExpose = true)),
                ),
                HaEntityRegistryEntry(
                    entityId = "light.secret",
                    options = HaEntityRegistryOptions(conversation = HaConversationEntityOptions(shouldExpose = false)),
                ),
                HaEntityRegistryEntry(entityId = "switch.no_options"),
            )

        assertEquals(
            HaVoiceExposureSnapshot(
                exposedEntityIds = setOf("light.office"),
                loaded = true,
            ),
            extractConversationExposureFromEntityRegistry(entries),
        )
    }

    @Test fun `entity registry fallback is unloaded when registry is empty`() {
        assertEquals(
            HaVoiceExposureSnapshot(exposedEntityIds = emptySet(), loaded = false),
            extractConversationExposureFromEntityRegistry(emptyList()),
        )
    }

    @Test fun `decodes entity registry aliases list`() {
        val entry =
            haJson.decodeFromString<HaEntityRegistryEntry>(
                """
                {
                  "entity_id": "light.office",
                  "aliases": ["Desk Lights", "Work Room"],
                  "options": {
                    "conversation": {
                      "should_expose": true
                    }
                  }
                }
                """.trimIndent(),
            )

        assertEquals(listOf("Desk Lights", "Work Room"), entry.aliases)
        assertEquals(true, entry.options.conversation?.shouldExpose)
    }

    @Test fun `returns unloaded snapshot for unsupported shape`() {
        val payload = JsonObject(emptyMap())

        assertEquals(
            HaVoiceExposureSnapshot(exposedEntityIds = emptySet(), loaded = false),
            extractConversationExposure(payload),
        )
    }
}
