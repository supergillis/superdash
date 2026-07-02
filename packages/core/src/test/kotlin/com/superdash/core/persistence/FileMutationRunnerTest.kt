package com.superdash.core.persistence

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FileMutationRunnerTest {
    @Test
    fun `mutations run one at a time`() =
        runTest {
            val runner = SerializedFileMutationRunner(dispatcher = StandardTestDispatcher(testScheduler))
            val events = mutableListOf<String>()
            val firstStarted = CompletableDeferred<Unit>()
            val releaseFirst = CompletableDeferred<Unit>()

            val first =
                async {
                    runner.mutate {
                        events += "first-start"
                        firstStarted.complete(Unit)
                        releaseFirst.await()
                        events += "first-end"
                    }
                }
            firstStarted.await()
            val second =
                async {
                    runner.mutate {
                        events += "second-start"
                        events += "second-end"
                    }
                }
            advanceUntilIdle()
            releaseFirst.complete(Unit)

            first.await()
            second.await()

            assertEquals(
                listOf("first-start", "first-end", "second-start", "second-end"),
                events,
            )
        }
}
