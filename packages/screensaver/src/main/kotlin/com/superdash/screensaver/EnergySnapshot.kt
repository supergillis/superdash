package com.superdash.screensaver

import com.superdash.ha.EntityState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Pure parser of three HA power sensors into a single typed snapshot.
 *
 *  Values are normalized to watts. The `unit_of_measurement` attribute
 *  (when present and equal to "kW") is honoured to upscale the raw
 *  numeric `state`. Sensors reporting `unknown`/`unavailable`/non-numeric
 *  states contribute a null field; if all three fields are null, the
 *  snapshot itself is null (so the HUD row hides entirely). */
data class EnergySnapshot(
    val usageW: Double?,
    val solarW: Double?,
    val gridW: Double?,
) {
    companion object {
        fun fromEntities(
            usage: EntityState?,
            solar: EntityState?,
            grid: EntityState?,
        ): EnergySnapshot? {
            val usageWatts = parseWatts(usage)
            val solarWatts = parseWatts(solar)
            val gridWatts = parseWatts(grid)
            if (usageWatts == null && solarWatts == null && gridWatts == null) {
                return null
            }
            return EnergySnapshot(usageW = usageWatts, solarW = solarWatts, gridW = gridWatts)
        }

        private fun parseWatts(entity: EntityState?): Double? {
            if (entity == null) {
                return null
            }
            val raw = entity.state.toDoubleOrNull() ?: return null
            val unit = entity.attributes.unitOfMeasurement()
            return if (unit.equals("kW", ignoreCase = true)) {
                raw * 1000.0
            } else {
                raw
            }
        }

        private fun JsonObject.unitOfMeasurement(): String? {
            val element = this["unit_of_measurement"] as? JsonPrimitive ?: return null
            return if (element.isString) element.content else null
        }
    }
}
