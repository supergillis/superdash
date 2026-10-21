package com.superdash.device

import com.superdash.core.persistence.KeyValueStore
import com.superdash.core.persistence.Setting
import com.superdash.core.persistence.mutate
import java.util.UUID

/** Owns a random device id, generated once, for devices that report no ANDROID_ID. */
class DeviceIdRepository(
    private val store: KeyValueStore,
    private val newId: () -> String = { UUID.randomUUID().toString().replace("-", "") },
) {
    suspend fun fallbackId(): String = store.mutate(FALLBACK_ID) { current -> current.ifEmpty(newId) }

    private companion object {
        val FALLBACK_ID = Setting(key = "device_fallback_id", default = "")
    }
}
