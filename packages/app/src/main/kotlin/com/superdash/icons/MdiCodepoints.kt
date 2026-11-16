package com.superdash.icons

import android.content.res.AssetManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val MDI_PREFIX = "mdi:"

@Serializable
private class CodepointsFile(
    val icons: Map<String, Int>,
    val aliases: Map<String, String>,
)

class MdiCodepoints(
    codepointsJson: String,
) {
    private val file = Json.decodeFromString<CodepointsFile>(codepointsJson)

    /** Every real icon name, sorted. Aliases are left out so the picker shows each glyph once. */
    val names: List<String> = file.icons.keys.sorted()

    fun codepoint(name: String): Int? {
        val bareName = name.removePrefix(MDI_PREFIX)
        return file.icons[bareName] ?: file.aliases[bareName]?.let { target -> file.icons[target] }
    }

    /**
     * Prefix matches come first, then the other substring matches, each group alphabetical.
     * Aliases match too but return the real name.
     */
    fun search(query: String): List<String> {
        val normalizedQuery = query.trim().removePrefix(MDI_PREFIX).lowercase()
        if (normalizedQuery.isEmpty()) {
            return names
        }
        val prefixMatches = matches { candidate -> candidate.startsWith(normalizedQuery) }
        val substringMatches = matches { candidate -> normalizedQuery in candidate } - prefixMatches
        return prefixMatches.toList() + substringMatches.toList()
    }

    private fun matches(predicate: (String) -> Boolean): Set<String> =
        (file.icons.keys.filter(predicate) + file.aliases.filterKeys(predicate).values).toSortedSet()

    companion object {
        private const val ASSET_PATH = "mdi/codepoints.json"

        @Volatile
        private var shared: MdiCodepoints? = null

        /** Parsing the file is slow, so every caller in the process shares one instance. */
        fun shared(assets: AssetManager): MdiCodepoints =
            shared ?: synchronized(this) {
                shared ?: MdiCodepoints(
                    assets.open(ASSET_PATH).bufferedReader().use { reader -> reader.readText() },
                ).also { loaded -> shared = loaded }
            }
    }
}
