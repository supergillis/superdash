package com.superdash.screensaver.slideshow

import com.superdash.immich.ImmichAssetOrientation
import com.superdash.immich.ImmichCatalogEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImmichCatalogStoreTest {
    @Test
    fun `load returns null when nothing stored`() =
        runTest {
            val store = InMemoryImmichCatalogStore()
            assertNull(store.load())
        }

    @Test
    fun `save then load round trips`() =
        runTest {
            val store = InMemoryImmichCatalogStore()
            val entries =
                listOf(
                    ImmichCatalogEntry("a", "IMAGE", ImmichAssetOrientation.Landscape),
                    ImmichCatalogEntry("b", "VIDEO", ImmichAssetOrientation.Unknown),
                )
            store.save(album = "Tablet", entries = entries, fetchedAtMs = 100L)

            val loaded = store.load()!!
            assertEquals("Tablet", loaded.album)
            assertEquals(entries, loaded.entries)
            assertEquals(100L, loaded.fetchedAtMs)
        }

    @Test
    fun `clear removes stored payload`() =
        runTest {
            val store = InMemoryImmichCatalogStore()
            store.save(album = "", entries = emptyList(), fetchedAtMs = 0L)
            store.clear()
            assertNull(store.load())
        }
}
