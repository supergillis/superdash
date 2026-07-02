package com.superdash.settings

import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.observe
import com.superdash.core.persistence.write
import com.superdash.kiosk.SidebarAction
import com.superdash.kiosk.SidebarPosition
import com.superdash.kiosk.SidebarSettings
import com.superdash.kiosk.SidebarSettingsDefaults
import com.superdash.kiosk.SidebarShortcut
import com.superdash.kiosk.defaultShortLabel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

internal class SettingsRepositorySidebarSettings(
    private val store: KeyValueStore,
) : SidebarSettings {
    override val position: Flow<SidebarPosition> =
        store.observe(POSITION).map { value -> SidebarPosition.fromKey(value) }

    override val pinned: Flow<Boolean> = store.observe(PINNED)

    override val showLabels: Flow<Boolean> = store.observe(SHOW_LABELS)

    override val shortcuts: Flow<List<SidebarShortcut>> =
        store.observe(SHORTCUTS_JSON).map { value -> decodeShortcuts(value) }

    override suspend fun setPosition(value: SidebarPosition) = store.write(POSITION, value.key)

    override suspend fun setPinned(value: Boolean) = store.write(PINNED, value)

    override suspend fun setShowLabels(value: Boolean) = store.write(SHOW_LABELS, value)

    override suspend fun setShortcuts(value: List<SidebarShortcut>) {
        store.write(SHORTCUTS_JSON, encodeShortcuts(value))
    }

    private fun decodeShortcuts(value: String): List<SidebarShortcut> =
        runCatching {
            val shortcutElements = json.decodeFromString<JsonArray>(value)
            shortcutElements.map { element ->
                val shortcut = json.decodeFromJsonElement(SidebarShortcut.serializer(), element)
                val shortcutObject = element as? JsonObject
                if (shortcutObject == null) {
                    shortcut
                } else {
                    shortcut.withLegacyShortLabelCustomization(shortcutObject)
                }
            }
        }.getOrDefault(SidebarSettingsDefaults.shortcuts)
            .normalizedShortcuts()

    private fun encodeShortcuts(value: List<SidebarShortcut>): String =
        json.encodeToString(shortcutsSerializer, value.normalizedShortcuts())

    private fun List<SidebarShortcut>.normalizedShortcuts(): List<SidebarShortcut> {
        val supportedShortcuts =
            filterNot { shortcut ->
                shortcut.action is SidebarAction.DismissScreensaver
            }
        val nonSettingsShortcuts =
            supportedShortcuts.filterNot { shortcut ->
                shortcut.action is SidebarAction.OpenSettings
            }
        val settingsShortcut =
            supportedShortcuts.firstOrNull { shortcut -> shortcut.action is SidebarAction.OpenSettings }
                ?: SidebarSettingsDefaults.settingsShortcut
        return (listOf(settingsShortcut) + nonSettingsShortcuts).map { shortcut ->
            shortcut.withNormalizedShortLabel()
        }
    }

    private fun SidebarShortcut.withNormalizedShortLabel(): SidebarShortcut {
        val trimmedShortLabel = shortLabel?.trim().orEmpty()
        val normalizedShortLabel =
            if (shortLabelCustomized) {
                trimmedShortLabel.ifBlank { null }
            } else {
                action.defaultShortLabel
            }
        return copy(shortLabel = normalizedShortLabel)
    }

    private fun SidebarShortcut.withLegacyShortLabelCustomization(shortcutObject: JsonObject): SidebarShortcut {
        if ("shortLabelCustomized" in shortcutObject) {
            return this
        }
        val labelElement = shortcutObject["shortLabel"] ?: return this
        val label = labelElement.jsonPrimitive.content
        val trimmedLabel = label.trim()
        return if (trimmedLabel.isNotBlank() && trimmedLabel !in generatedShortLabels) {
            copy(shortLabelCustomized = true)
        } else {
            this
        }
    }

    private companion object {
        val json =
            Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }
        val shortcutsSerializer = ListSerializer(SidebarShortcut.serializer())
        val POSITION =
            Setting(
                key = "sidebar.position",
                default = SidebarSettingsDefaults.position.key,
                read = { value -> SidebarPosition.fromKey(value).key },
                write = { value -> SidebarPosition.fromKey(value).key },
            )
        val PINNED = Setting(key = "sidebar.pinned", default = SidebarSettingsDefaults.pinned)
        val SHOW_LABELS = Setting(key = "sidebar.showLabels", default = SidebarSettingsDefaults.showLabels)
        val generatedShortLabels =
            setOf(
                SidebarAction.OpenSettings.defaultShortLabel,
                SidebarAction.ReloadDashboard.defaultShortLabel,
                SidebarAction.ShowScreensaver.defaultShortLabel,
                SidebarAction.SetNightModeActive(active = true).defaultShortLabel,
                SidebarAction.SetNightModeActive(active = false).defaultShortLabel,
                SidebarAction.OpenDashboardPath("").defaultShortLabel,
            )
        val SHORTCUTS_JSON =
            Setting(
                key = "sidebar.shortcuts",
                default = json.encodeToString(shortcutsSerializer, SidebarSettingsDefaults.shortcuts),
                read = { value ->
                    if (value.isBlank()) {
                        json.encodeToString(shortcutsSerializer, SidebarSettingsDefaults.shortcuts)
                    } else {
                        value
                    }
                },
            )
    }
}
