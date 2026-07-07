package com.superdash.core.persistence

import androidx.datastore.core.DataStore
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

class DataStoreKeyValueStoreTest {
    @Test
    fun `flow returns default when key missing`() =
        runTest {
            val store = DataStoreKeyValueStore(FakeDataStore())
            assertEquals("fallback", store.flow("k", "fallback").first())
        }

    @Test
    fun `flow returns stored value when key present`() =
        runTest {
            val backing = FakeDataStore()
            val store = DataStoreKeyValueStore(backing)
            store.set("k", "stored")
            assertEquals("stored", store.flow("k", "fallback").first())
        }

    @Test
    fun `set writes value of correct type`() =
        runTest {
            val backing = FakeDataStore()
            val store = DataStoreKeyValueStore(backing)
            store.set("b", true)
            store.set("i", 42)
            store.set("s", "hello")
            assertEquals(true, backing.data.first()[booleanPreferencesKey("b")])
            assertEquals(42, backing.data.first()[intPreferencesKey("i")])
            assertEquals("hello", backing.data.first()[stringPreferencesKey("s")])
        }

    @Test
    fun `mutate writes transformed value`() =
        runTest {
            val store = DataStoreKeyValueStore(FakeDataStore())
            store.set("count", 1)

            val updated = store.mutate("count", 0) { value -> value + 1 }

            assertEquals(2, updated)
            assertEquals(2, store.flow("count", 0).first())
        }

    @Test
    fun `set throws on unsupported type`() =
        runTest {
            val store = DataStoreKeyValueStore(FakeDataStore())
            assertThrows(IllegalStateException::class.java) {
                kotlinx.coroutines.runBlocking { store.set("x", CustomType("y")) }
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
    }
}
