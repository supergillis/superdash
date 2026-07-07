package com.superdash.ha

/** Pure function that applies a state_changed HA event to the entity map.
 *  Public for unit testing. */
fun applyEntityEvent(map: Map<String, EntityState>, event: HaEvent): Map<String, EntityState> {
    if (event.eventType != "state_changed") {
        return map
    }
    val data = haJson.decodeFromJsonElement(StateChangedEventData.serializer(), event.data)
    val entityId = data.entityId
    val newState = data.newState
    return map.toMutableMap().apply {
        if (newState == null) {
            remove(entityId)
        } else {
            put(entityId, newState)
        }
    }
}
