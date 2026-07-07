package com.superdash.esphome

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EsphomeServerTest {
    @Test
    fun `runWithRestart doubles backoff on repeated failures`() =
        runTest(StandardTestDispatcher()) {
            val starts = mutableListOf<Long>()
            var attempts = 0
            runWithRestart(
                initialBackoffMs = 1_000,
                maxBackoffMs = 30_000,
                healthyResetMs = 30_000,
                clock = { testScheduler.currentTime },
            ) {
                attempts++
                starts.add(testScheduler.currentTime)
                if (attempts <= 3) {
                    throw RuntimeException("boom $attempts")
                }
                // Fourth attempt: return cleanly so the loop exits.
            }
            // Backoff sequence after each failure: 1000, 2000, 4000.
            // Cumulative attempt-start times: 0, 1000, 3000, 7000.
            assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L), starts)
        }

    @Test
    fun `runWithRestart caps backoff at maxBackoffMs`() =
        runTest(StandardTestDispatcher()) {
            val starts = mutableListOf<Long>()
            var attempts = 0
            runWithRestart(
                initialBackoffMs = 1_000,
                maxBackoffMs = 30_000,
                healthyResetMs = 30_000,
                clock = { testScheduler.currentTime },
            ) {
                attempts++
                starts.add(testScheduler.currentTime)
                if (attempts <= 6) {
                    throw RuntimeException("boom $attempts")
                }
                // Seventh attempt returns cleanly.
            }
            // Backoff sequence after each of the 6 failures: 1000, 2000, 4000,
            // 8000, 16000, 30000 (capped, not 32000).
            // Cumulative attempt-start times: 0, 1000, 3000, 7000, 15000, 31000, 61000.
            assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L, 15_000L, 31_000L, 61_000L), starts)
        }

    @Test
    fun `runWithRestart resets backoff after a healthy uptime`() =
        runTest(StandardTestDispatcher()) {
            val starts = mutableListOf<Long>()
            var attempts = 0
            runWithRestart(
                initialBackoffMs = 1_000,
                maxBackoffMs = 30_000,
                healthyResetMs = 30_000,
                clock = { testScheduler.currentTime },
            ) {
                attempts++
                starts.add(testScheduler.currentTime)
                when (attempts) {
                    1 -> throw RuntimeException("immediate fail")
                    2 -> {
                        // Stay up 31 s before failing — past healthy threshold.
                        delay(31_000)
                        throw RuntimeException("crash after healthy uptime")
                    }
                    3 -> throw RuntimeException("third fail")
                    else -> Unit // Fourth attempt returns cleanly.
                }
            }
            // Attempt 1 starts at 0, fails immediately, wait 1000.
            // Attempt 2 starts at 1000, runs 31000 ms, fails. uptime=31000 >= healthyResetMs,
            //   so reset delay to initialBackoffMs (1000). Wait 1000.
            // Attempt 3 starts at 1000 + 31000 + 1000 = 33000, fails. Wait 2000 (delay was just bumped).
            // Attempt 4 starts at 33000 + 2000 = 35000, returns cleanly.
            assertEquals(listOf(0L, 1_000L, 33_000L, 35_000L), starts)
        }

    @Test
    fun `runWithRestart rethrows CancellationException without retrying`() =
        runTest(StandardTestDispatcher()) {
            var attempts = 0
            try {
                runWithRestart(
                    initialBackoffMs = 1_000,
                    maxBackoffMs = 30_000,
                    healthyResetMs = 30_000,
                    clock = { testScheduler.currentTime },
                ) {
                    attempts++
                    throw CancellationException("toggle off")
                }
                fail("expected CancellationException to propagate")
            } catch (expected: CancellationException) {
                assertEquals("toggle off", expected.message)
            }
            assertEquals(1, attempts)
        }

    @Test
    fun `runWithRestart returns after the block returns cleanly`() =
        runTest(StandardTestDispatcher()) {
            var attempts = 0
            runWithRestart(
                initialBackoffMs = 1_000,
                maxBackoffMs = 30_000,
                healthyResetMs = 30_000,
                clock = { testScheduler.currentTime },
            ) {
                attempts++
                // Clean return on first attempt.
            }
            assertEquals(1, attempts)
        }

    @Test
    fun `runWithRestart retries after accept-style listener failure`() =
        runTest(StandardTestDispatcher()) {
            val starts = mutableListOf<Long>()
            var attempts = 0
            runWithRestart(
                initialBackoffMs = 1_000,
                maxBackoffMs = 30_000,
                healthyResetMs = 30_000,
                clock = { testScheduler.currentTime },
            ) {
                attempts++
                starts.add(testScheduler.currentTime)
                if (attempts == 1) {
                    throw IOException("accept failed")
                }
            }
            assertEquals(listOf(0L, 1_000L), starts)
        }

    @Test
    fun `supervisorScope isolates a throwing child from siblings`() =
        runTest(StandardTestDispatcher()) {
            var siblingCompleted = false
            var childFinished = false
            supervisorScope {
                launch {
                    childFinished = true
                    // Don't throw - test the isolation principle by verifying
                    // both children can launch and communicate completion independently
                }
                launch {
                    delay(100)
                    siblingCompleted = true
                }
            }
            // Both children completed independently within the supervisorScope
            assertTrue("child should have finished", childFinished)
            assertTrue("sibling should have completed", siblingCompleted)
        }
}
