package com.superdash.voice

import com.superdash.ha.EntityState
import com.superdash.ha.HaArea
import com.superdash.ha.HaDeviceRegistryEntry
import com.superdash.ha.HaEntityRegistryEntry
import com.superdash.ha.HaServiceCall
import com.superdash.ha.HaServiceTarget
import com.superdash.voice.intent.registry.LocalGeneratedIntentStatus
import com.superdash.voice.intent.registry.LocalGeneratedIntentTargetKind
import com.superdash.voice.intent.registry.LocalIntentRegistryMetadata
import com.superdash.voice.intent.registry.buildLocalIntentRegistry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalIntentRegistryBuilderTest {
    @Test fun `generates turn on phrase from friendly name`() {
        val registry =
            buildLocalIntentRegistry(
                entities =
                    mapOf(
                        "light.kitchen" to entity("light.kitchen", "Kitchen Lights"),
                        "switch.kitchen" to entity("switch.kitchen", "Kitchen Switch"),
                    ),
                metadata = metadata(setOf("light.kitchen", "switch.kitchen")),
            )

        val command = registry.commandForPhrase("turn on kitchen lights")

        assertEquals(
            HaServiceCall(
                domain = "light",
                service = "turn_on",
                target = HaServiceTarget(entityId = listOf("light.kitchen")),
            ),
            command?.action?.call,
        )
    }

    @Test fun `duplicate generated phrases are marked ambiguous`() {
        val registry =
            buildLocalIntentRegistry(
                entities =
                    mapOf(
                        "light.kitchen_main" to entity("light.kitchen_main", "Kitchen"),
                        "light.kitchen_counter" to entity("light.kitchen_counter", "Kitchen"),
                    ),
                metadata = metadata(setOf("light.kitchen_main", "light.kitchen_counter")),
            )

        val result = registry.lookup("turn on kitchen")

        assertEquals(LocalGeneratedIntentStatus.Ambiguous, result.status)
        assertEquals(listOf("light.kitchen_counter", "light.kitchen_main"), result.candidates.sorted())
    }

    @Test fun `brightness phrase carries percent payload`() {
        val registry =
            buildLocalIntentRegistry(
                mapOf("light.hallway" to entity("light.hallway", "Hallway")),
                metadata = metadata(setOf("light.hallway")),
            )

        val command = registry.commandForPhrase("set hallway to twenty percent")

        assertEquals("light", command?.action?.call?.domain)
        assertEquals("turn_on", command?.action?.call?.service)
        assertEquals(
            JsonPrimitive(20),
            command
                ?.action
                ?.call
                ?.serviceData
                ?.get("brightness_pct"),
        )
    }

    @Test fun `light entity id generates lights alias`() {
        val registry =
            buildLocalIntentRegistry(
                mapOf("light.desk" to entity("light.desk", "Desk Lamp")),
                metadata = metadata(setOf("light.desk")),
            )

        val command = registry.commandForPhrase("turn on desk lights")

        assertEquals(
            HaServiceCall(
                domain = "light",
                service = "turn_on",
                target = HaServiceTarget(entityId = listOf("light.desk")),
            ),
            command?.action?.call,
        )
    }

    @Test fun `only exposed entities generate commands`() {
        val registry =
            buildLocalIntentRegistry(
                entities =
                    mapOf(
                        "light.office" to entity("light.office", "Office Lights"),
                        "light.secret" to entity("light.secret", "Secret Lights"),
                    ),
                metadata = metadata(setOf("light.office")),
            )

        assertEquals(LocalGeneratedIntentStatus.Matched, registry.lookup("turn on office lights").status)
        assertEquals(LocalGeneratedIntentStatus.Unknown, registry.lookup("turn on secret lights").status)
    }

    @Test fun `entity registry aliases generate commands`() {
        val registry =
            buildLocalIntentRegistry(
                entities = mapOf("light.office" to entity("light.office", "Office Lights")),
                metadata =
                    metadata(
                        exposedEntityIds = setOf("light.office"),
                        registryEntries =
                            mapOf(
                                "light.office" to
                                    registryEntry(
                                        entityId = "light.office",
                                        aliases = listOf("Work Room", "Desk Zone"),
                                    ),
                            ),
                    ),
            )

        assertEquals(LocalGeneratedIntentStatus.Matched, registry.lookup("turn on work room").status)
        assertEquals(LocalGeneratedIntentStatus.Matched, registry.lookup("turn on desk zone").status)
    }

    @Test fun `entity registry aliases work without friendly name`() {
        val registry =
            buildLocalIntentRegistry(
                entities = mapOf("light.office" to entityWithoutFriendlyName("light.office")),
                metadata =
                    metadata(
                        exposedEntityIds = setOf("light.office"),
                        registryEntries =
                            mapOf(
                                "light.office" to registryEntry("light.office", aliases = listOf("Work Room")),
                            ),
                    ),
            )

        val command = registry.commandForPhrase("turn on work room")

        assertEquals(
            HaServiceCall(
                domain = "light",
                service = "turn_on",
                target = HaServiceTarget(entityId = listOf("light.office")),
            ),
            command?.action?.call,
        )
    }

    @Test fun `area phrase targets exposed entities in area`() {
        val registry =
            buildLocalIntentRegistry(
                entities =
                    mapOf(
                        "light.office_ceiling" to entity("light.office_ceiling", "Ceiling"),
                        "switch.office_fan" to entity("switch.office_fan", "Fan"),
                        "light.secret" to entity("light.secret", "Secret"),
                    ),
                areas = mapOf("office" to HaArea(areaId = "office", name = "Office")),
                metadata =
                    metadata(
                        exposedEntityIds = setOf("light.office_ceiling", "switch.office_fan", "light.secret"),
                        registryEntries =
                            mapOf(
                                "light.office_ceiling" to registryEntry("light.office_ceiling", areaId = "office"),
                                "switch.office_fan" to registryEntry("switch.office_fan", areaId = "office"),
                                "light.secret" to registryEntry("light.secret", areaId = "hidden"),
                            ),
                    ),
            )

        val command = registry.commandForPhrase("turn on office")

        assertEquals(
            HaServiceTarget(entityId = listOf("light.office_ceiling", "switch.office_fan")),
            command?.action?.call?.target,
        )
    }

    @Test fun `area lights phrase targets exposed lights in area`() {
        val registry =
            buildLocalIntentRegistry(
                entities =
                    mapOf(
                        "light.office_ceiling" to entity("light.office_ceiling", "Ceiling"),
                        "switch.office_fan" to entity("switch.office_fan", "Fan"),
                    ),
                areas = mapOf("office" to HaArea(areaId = "office", name = "Office")),
                metadata =
                    metadata(
                        exposedEntityIds = setOf("light.office_ceiling", "switch.office_fan"),
                        registryEntries =
                            mapOf(
                                "light.office_ceiling" to registryEntry("light.office_ceiling", areaId = "office"),
                                "switch.office_fan" to registryEntry("switch.office_fan", areaId = "office"),
                            ),
                    ),
            )

        val command = registry.commandForPhrase("turn on office lights")

        assertEquals(
            HaServiceCall(
                domain = "light",
                service = "turn_on",
                target = HaServiceTarget(entityId = listOf("light.office_ceiling")),
            ),
            command?.action?.call,
        )
    }

    @Test fun `area lights phrase uses device area when entity area is empty`() {
        val registry =
            buildLocalIntentRegistry(
                entities =
                    mapOf(
                        "light.desk_ceiling" to entity("light.desk_ceiling", "Ceiling"),
                        "switch.desk_fan" to entity("switch.desk_fan", "Fan"),
                    ),
                areas = mapOf("desk" to HaArea(areaId = "desk", name = "Desk")),
                metadata =
                    metadata(
                        exposedEntityIds = setOf("light.desk_ceiling", "switch.desk_fan"),
                        registryEntries =
                            mapOf(
                                "light.desk_ceiling" to registryEntry("light.desk_ceiling", deviceId = "device.desk"),
                                "switch.desk_fan" to registryEntry("switch.desk_fan", deviceId = "device.desk"),
                            ),
                        deviceRegistry =
                            mapOf(
                                "device.desk" to HaDeviceRegistryEntry(deviceId = "device.desk", areaId = "desk"),
                            ),
                    ),
            )

        val command = registry.commandForPhrase("turn on desk lights")

        assertEquals(
            HaServiceCall(
                domain = "light",
                service = "turn_on",
                target = HaServiceTarget(entityId = listOf("light.desk_ceiling")),
            ),
            command?.action?.call,
        )
    }

    @Test fun `plural area lights phrase wins over entity phrase`() {
        val registry =
            buildLocalIntentRegistry(
                entities =
                    mapOf(
                        "light.desk_ceiling" to entity("light.desk_ceiling", "Ceiling"),
                        "light.desk_named_lights" to entity("light.desk_named_lights", "Desk Lights"),
                        "switch.desk_fan" to entity("switch.desk_fan", "Fan"),
                    ),
                areas = mapOf("desk" to HaArea(areaId = "desk", name = "Desk")),
                metadata =
                    metadata(
                        exposedEntityIds = setOf("light.desk_ceiling", "light.desk_named_lights", "switch.desk_fan"),
                        registryEntries =
                            mapOf(
                                "light.desk_ceiling" to registryEntry("light.desk_ceiling", areaId = "desk"),
                                "light.desk_named_lights" to registryEntry("light.desk_named_lights", areaId = "other"),
                                "switch.desk_fan" to registryEntry("switch.desk_fan", areaId = "desk"),
                            ),
                    ),
            )

        val command = registry.commandForPhrase("turn on desk lights")

        assertEquals(
            HaServiceCall(
                domain = "light",
                service = "turn_on",
                target = HaServiceTarget(entityId = listOf("light.desk_ceiling")),
            ),
            command?.action?.call,
        )
        assertEquals(LocalGeneratedIntentTargetKind.Area, command?.targetKind)
    }

    @Test fun `area phrases take priority over entity phrases`() {
        val registry =
            buildLocalIntentRegistry(
                entities = mapOf("light.office" to entity("light.office", "Office")),
                areas = mapOf("office" to HaArea(areaId = "office", name = "Office")),
                metadata =
                    metadata(
                        exposedEntityIds = setOf("light.office"),
                        registryEntries = mapOf("light.office" to registryEntry("light.office", areaId = "office")),
                    ),
            )

        val command = registry.commandForPhrase("turn on office")

        assertEquals(HaServiceTarget(entityId = listOf("light.office")), command?.action?.call?.target)
        assertEquals(LocalGeneratedIntentTargetKind.Area, command?.targetKind)
    }

    @Test fun `area aliases take priority over entity aliases`() {
        val registry =
            buildLocalIntentRegistry(
                entities = mapOf("light.office" to entity("light.office", "Study")),
                areas = mapOf("office" to HaArea(areaId = "office", name = "Office", aliases = listOf("Study"))),
                metadata =
                    metadata(
                        exposedEntityIds = setOf("light.office"),
                        registryEntries = mapOf("light.office" to registryEntry("light.office", areaId = "office")),
                    ),
            )

        val command = registry.commandForPhrase("turn on study")

        assertEquals(HaServiceTarget(entityId = listOf("light.office")), command?.action?.call?.target)
        assertEquals(LocalGeneratedIntentTargetKind.Area, command?.targetKind)
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

    private fun entityWithoutFriendlyName(entityId: String): EntityState =
        EntityState(
            entityId = entityId,
            state = "off",
            attributes = JsonObject(emptyMap()),
        )

    private fun metadata(
        exposedEntityIds: Set<String>,
        registryEntries: Map<String, HaEntityRegistryEntry> = emptyMap(),
        deviceRegistry: Map<String, HaDeviceRegistryEntry> = emptyMap(),
    ): LocalIntentRegistryMetadata =
        LocalIntentRegistryMetadata(
            entityRegistry = registryEntries,
            deviceRegistry = deviceRegistry,
            exposedEntityIds = exposedEntityIds,
            loaded = true,
        )

    private fun registryEntry(
        entityId: String,
        areaId: String? = null,
        deviceId: String? = null,
        aliases: List<String> = emptyList(),
        name: String? = null,
        originalName: String? = null,
    ): HaEntityRegistryEntry =
        HaEntityRegistryEntry(
            entityId = entityId,
            areaId = areaId,
            deviceId = deviceId,
            aliases = aliases,
            name = name,
            originalName = originalName,
        )
}
