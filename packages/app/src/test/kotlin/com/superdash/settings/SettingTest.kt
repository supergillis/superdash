package com.superdash.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingTest {
    @Test
    fun `flow returns default when key is missing`() =
        runTest {
            val store = FakeDataStore()
            val setting = store.setting("k", default = "fallback")
            assertEquals("fallback", setting.flow.first())
        }

    @Test
    fun `flow returns stored value when key is present`() =
        runTest {
            val store = FakeDataStore()
            val setting = store.setting("k", default = "fallback")
            store.edit { it[stringPreferencesKey("k")] = "stored" }
            assertEquals("stored", setting.flow.first())
        }

    @Test
    fun `flow applies read transform to stored value`() =
        runTest {
            val store = FakeDataStore()
            val setting = store.setting("k", default = "", read = { it.trim().trim('/') })
            store.edit { it[stringPreferencesKey("k")] = "  /lovelace/home/  " }
            assertEquals("lovelace/home", setting.flow.first())
        }

    @Test
    fun `flow applies read transform to default value`() =
        runTest {
            val store = FakeDataStore()
            val setting = store.setting("k", default = " /raw/ ", read = { it.trim().trim('/') })
            assertEquals("raw", setting.flow.first())
        }

    @Test
    fun `set writes value through write transform`() =
        runTest {
            val store = FakeDataStore()
            val setting = store.setting("port", default = 2323, write = { it.coerceIn(1024, 65535) })
            setting.set(80)
            assertEquals(1024, store.data.first()[intPreferencesKey("port")])
            setting.set(9999)
            assertEquals(9999, store.data.first()[intPreferencesKey("port")])
        }

    @Test
    fun `get returns first value of flow`() =
        runTest {
            val store = FakeDataStore()
            val setting = store.setting("flag", default = false)
            assertEquals(false, setting.get())
            setting.set(true)
            assertEquals(true, setting.get())
        }

    @Test
    fun `factory wires Boolean type to booleanPreferencesKey`() =
        runTest {
            val store = FakeDataStore()
            val setting = store.setting("b", default = false)
            setting.set(true)
            assertEquals(true, store.data.first()[booleanPreferencesKey("b")])
        }

    @Test
    fun `factory wires Int type to intPreferencesKey`() =
        runTest {
            val store = FakeDataStore()
            val setting = store.setting("i", default = 0)
            setting.set(42)
            assertEquals(42, store.data.first()[intPreferencesKey("i")])
        }

    @Test
    fun `factory wires String type to stringPreferencesKey`() =
        runTest {
            val store = FakeDataStore()
            val setting = store.setting("s", default = "")
            setting.set("hello")
            assertEquals("hello", store.data.first()[stringPreferencesKey("s")])
        }

    @Test
    fun `factory throws for unsupported type`() {
        val store = FakeDataStore()
        assertThrows(IllegalStateException::class.java) {
            store.setting("custom", default = CustomType("x"))
        }
    }

    private data class CustomType(
        val value: String,
    )

    private class FakeDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow<Preferences>(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }

        suspend fun edit(block: (MutablePreferences) -> Unit): Preferences =
            updateData { current ->
                val mutable = current.toMutablePreferences()
                block(mutable)
                mutable.toPreferences()
            }
    }
}
