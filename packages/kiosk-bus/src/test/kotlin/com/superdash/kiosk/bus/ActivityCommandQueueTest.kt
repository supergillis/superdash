package com.superdash.kiosk.bus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityCommandQueueTest {
    @Test
    fun `attached collector receives submitted command`() =
        runTest {
            val queue = ActivityCommandQueue()
            val received = mutableListOf<ActivityCommand>()
            val job =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    queue.commands.collect { command -> received.add(command) }
                }

            queue.submit(ActivityCommand.RefreshWebView)
            advanceUntilIdle()

            assertEquals(listOf(ActivityCommand.RefreshWebView), received)
            job.cancel()
        }

    @Test
    fun `command submitted while detached is delivered when collector attaches`() =
        runTest {
            val queue = ActivityCommandQueue()

            queue.submit(ActivityCommand.RestartApp)
            advanceUntilIdle()

            // Activity attaches after the command was buffered.
            val received = queue.commands.first()
            assertEquals(ActivityCommand.RestartApp, received)
        }

    @Test
    fun `multiple buffered commands are delivered in submission order`() =
        runTest {
            val queue = ActivityCommandQueue()

            queue.submit(ActivityCommand.RefreshWebView)
            queue.submit(ActivityCommand.RestartApp)
            queue.submit(ActivityCommand.RefreshWebView)
            advanceUntilIdle()

            val received = queue.commands.take(3).toList()
            assertEquals(
                listOf(
                    ActivityCommand.RefreshWebView,
                    ActivityCommand.RestartApp,
                    ActivityCommand.RefreshWebView,
                ),
                received,
            )
        }

    @Test
    fun `each command is delivered to exactly one consumer`() =
        runTest {
            val queue = ActivityCommandQueue()
            val receivedA = mutableListOf<ActivityCommand>()
            val receivedB = mutableListOf<ActivityCommand>()
            val jobA =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    queue.commands.collect { receivedA.add(it) }
                }
            val jobB =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                    queue.commands.collect { receivedB.add(it) }
                }

            queue.submit(ActivityCommand.RefreshWebView)
            advanceUntilIdle()

            assertEquals(1, receivedA.size + receivedB.size)
            jobA.cancel()
            jobB.cancel()
        }

    @Test
    fun `commands submitted while first collector is cancelled are delivered to the next collector`() =
        runTest {
            val queue = ActivityCommandQueue()

            // Activity 1 attaches, collects nothing, then detaches.
            val jobA =
                backgroundScope.launch {
                    queue.commands.first()
                }
            jobA.cancel()
            advanceUntilIdle()

            // Command submitted while detached.
            queue.submit(ActivityCommand.RestartApp)
            advanceUntilIdle()

            // Activity 2 attaches.
            val received = queue.commands.first()
            assertEquals(ActivityCommand.RestartApp, received)
        }
}
