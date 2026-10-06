package com.superdash.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.datastore.preferences.core.edit
import com.superdash.AppGraph
import com.superdash.SuperdashApp
import com.superdash.core.json.coreJson
import com.superdash.core.log.Log
import com.superdash.feed.FeedConfig
import com.superdash.feed.FeedState
import com.superdash.ha.EntityState
import com.superdash.ha.HaServiceCall
import com.superdash.ha.HaServiceTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val log = Log("DebugTools")

private const val RESULT_ERROR = 1
private const val TEST_ENTITY_PREFIX = "input_boolean.superdash_test"
private val SECRET_KEY_MARKERS = listOf("api_key", "token", "password", "secret")

/**
 * Debug-only control surface for agents and `scripts/device/sd.ts`.
 *
 * The reply goes back as the broadcast result data, so `am broadcast` prints it
 * and callers never scrape logcat. Writes go through the same stores the app
 * observes, so they apply live.
 */
class DebugToolsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val graph = (context.applicationContext as SuperdashApp).graph
        CoroutineScope(Dispatchers.Default).launch {
            try {
                pendingResult.resultData = withTimeout(15_000) { handle(graph, intent) }
                pendingResult.resultCode = Activity.RESULT_OK
            } catch (t: Throwable) {
                log.w("debug command failed", t, "cmd" to intent.getStringExtra("cmd"))
                pendingResult.resultData = t.message ?: t::class.simpleName
                pendingResult.resultCode = RESULT_ERROR
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(graph: AppGraph, intent: Intent): String {
        val value = intent.getStringExtra("value_b64")?.let { String(Base64.decode(it, Base64.DEFAULT)) }
        return when (val command = intent.requireExtra("cmd")) {
            "dump" -> {
                dumpSettings(graph)
            }
            "set" -> {
                setSetting(graph, intent.requireExtra("key"), intent.getStringExtra("type"), value.required())
            }
            "remove" -> {
                removeSetting(graph, intent.requireExtra("key"))
            }
            "feed_state" -> {
                feedState(graph)
            }
            "feed_upsert" -> {
                upsertFeed(graph, value.required())
            }
            "feed_remove" -> {
                graph.feedSettings.removeFeed(intent.requireExtra("id"))
                "ok"
            }
            "feed_show" -> {
                showFeed(graph, intent.requireExtra("id"))
            }
            "feed_close" -> {
                graph.feedOverlayController.close()
                "ok"
            }
            "idle" -> {
                graph.idleController.forceIdle()
                "ok"
            }
            "wake" -> {
                graph.idleController.touch()
                "ok"
            }
            "ha_list" -> {
                listEntities(graph, intent.getStringExtra("prefix").orEmpty())
            }
            "ha_state" -> {
                entityState(graph, intent.requireExtra("entity"), value)
            }
            else -> {
                error("unknown cmd $command")
            }
        }
    }

    private suspend fun dumpSettings(graph: AppGraph): String {
        val prefs =
            graph.settings.dataStore.data
                .first()
                .asMap()
        val entries =
            prefs.entries.sortedBy { it.key.name }.associate { (key, stored) ->
                val redact = SECRET_KEY_MARKERS.any { key.name.contains(it) } && stored.toString().isNotEmpty()
                key.name to
                    when {
                        redact -> {
                            JsonPrimitive("<redacted>")
                        }
                        stored is Boolean -> {
                            JsonPrimitive(stored)
                        }
                        stored is Number -> {
                            JsonPrimitive(stored)
                        }
                        else -> {
                            JsonPrimitive(stored.toString())
                        }
                    }
            }
        return JsonObject(entries).toString()
    }

    /** Writing a key under a different type than it is stored with leaves two entries the
     *  app cannot tell apart, so an untyped set reuses the stored value's type. */
    private suspend fun setSetting(graph: AppGraph, key: String, type: String?, raw: String): String {
        val stored =
            graph.settings.dataStore.data
                .first()
                .asMap()
                .entries
                .firstOrNull { it.key.name == key }
                ?.value
        val typed: Any =
            when (type ?: stored?.let { typeName(it) } ?: error("$key is not stored yet; pass a type")) {
                "bool" -> {
                    raw.toBooleanStrict()
                }
                "int" -> {
                    raw.toInt()
                }
                "long" -> {
                    raw.toLong()
                }
                "float" -> {
                    raw.toFloat()
                }
                "double" -> {
                    raw.toDouble()
                }
                "string" -> {
                    raw
                }
                else -> {
                    error("unknown type $type")
                }
            }
        graph.keyValueStore.set(key, typed)
        log.i("set setting", "key" to key)
        return "ok"
    }

    private suspend fun removeSetting(graph: AppGraph, key: String): String {
        graph.settings.dataStore.edit { prefs ->
            prefs
                .asMap()
                .keys
                .filter { it.name == key }
                .forEach { prefs.remove(it) }
        }
        return "ok"
    }

    private suspend fun feedState(graph: AppGraph): String {
        val shown = graph.feedOverlayController.state.value as? FeedState.Showing
        return buildJsonObject {
            put("enabled", graph.feedSettings.enabled.first())
            put("idle", graph.idleController.isIdle.value)
            put("showing", shown?.config?.id)
            put(
                "active",
                JsonObject(
                    graph.feedWatcher.activeFeeds.value
                        .mapValues { JsonPrimitive(it.value) },
                ),
            )
            put("feeds", coreJson.parseToJsonElement(FeedConfig.encodeList(graph.feedSettings.feeds.first())))
        }.toString()
    }

    private suspend fun upsertFeed(graph: AppGraph, json: String): String {
        val config = coreJson.decodeFromString(FeedConfig.serializer(), json)
        graph.feedSettings.upsertFeed(config)
        return "ok ${config.id}"
    }

    private suspend fun showFeed(graph: AppGraph, id: String): String {
        require(
            graph.feedSettings.feeds
                .first()
                .any { it.id == id },
        ) { "no feed with id $id" }
        graph.feedOverlayController.showById(id)
        return "ok"
    }

    private fun listEntities(graph: AppGraph, prefix: String): String {
        val states =
            graph.haClient.entities.value
                .filterKeys { it.startsWith(prefix) }
                .mapValues { it.value.state }
                .toSortedMap()
        return coreJson.encodeToString(MapSerializer(String.serializer(), String.serializer()), states)
    }

    /** Reads an entity, or toggles a test helper and waits until the app's own entity
     *  cache sees the new state, so a following command observes it too. */
    private suspend fun entityState(graph: AppGraph, entityId: String, state: String?): String {
        if (state != null) {
            require(entityId.startsWith(TEST_ENTITY_PREFIX)) { "only $TEST_ENTITY_PREFIX* entities may be written" }
            require(state == "on" || state == "off") { "state must be on or off" }
            val result =
                graph.haServiceCalls.callService(
                    HaServiceCall(
                        "input_boolean",
                        "turn_$state",
                        target = HaServiceTarget(entityId = listOf(entityId)),
                    ),
                )
            check(result.success) { "HA rejected input_boolean.turn_$state" }
            graph.haClient.observeEntity(entityId).first { it?.state == state }
        }
        val entity = graph.haClient.entities.value[entityId] ?: error("$entityId not found")
        return coreJson.encodeToString(EntityState.serializer(), entity)
    }
}

private fun Intent.requireExtra(name: String): String =
    getStringExtra(name)?.takeIf { it.isNotBlank() } ?: error("missing $name extra")

private fun String?.required(): String = this ?: error("missing value")

private fun typeName(stored: Any): String =
    when (stored) {
        is Boolean -> {
            "bool"
        }
        is Int -> {
            "int"
        }
        is Long -> {
            "long"
        }
        is Float -> {
            "float"
        }
        is Double -> {
            "double"
        }
        else -> {
            "string"
        }
    }
