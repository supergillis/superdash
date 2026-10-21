package com.superdash.device

import com.superdash.core.persistence.InMemoryKeyValueStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIdRepositoryTest {
    @Test
    fun `fallback id is generated once and then reused`() =
        runTest {
            val generatedIds = listOf("first", "second").iterator()
            val repository = DeviceIdRepository(InMemoryKeyValueStore()) { generatedIds.next() }
            assertEquals("first", repository.fallbackId())
            assertEquals("first", repository.fallbackId())
        }
}
