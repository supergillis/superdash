package com.superdash.esphome

import android.content.Context
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Test

class EsphomeServerMdnsSwapTest {
    private val context: Context = android.app.Application()

    private val deviceInfo =
        EsphomeDeviceInfo(
            name = "test-device",
            macAddress = "AA:BB:CC:DD:EE:FF",
            esphomeVersion = "2026.4.5",
            compilationTime = "",
            model = "Test",
            manufacturer = "Acme",
            friendlyName = "Test Device",
        )

    private inner class CountingMdns : EsphomeMdns(context, deviceInfo, noiseEnabled = false) {
        var starts = 0
        var stops = 0

        override fun start() {
            starts++
        }

        override fun stop() {
            stops++
        }
    }

    @Suppress("OPT_IN_USAGE")
    private fun makeServer(initialMdns: CountingMdns): EsphomeServer =
        EsphomeServer(
            scope = kotlinx.coroutines.GlobalScope,
            enabled = flowOf(false),
            deviceInfo = deviceInfo,
            entities = { emptyList() },
            noiseConfig = { EsphomeNoiseConfig.PlainOnly },
            mdns = initialMdns,
        )

    @Test
    fun `swapMdns does not start or stop when server is inactive`() {
        val first = CountingMdns()
        val second = CountingMdns()
        val server = makeServer(first)

        // mdnsActive is false — no start() has been called on the server.
        server.swapMdns(second)

        assertEquals("old.stop should not fire while inactive", 0, first.stops)
        assertEquals("old.start should never fire", 0, first.starts)
        assertEquals("new.start should not fire while inactive", 0, second.starts)
        assertEquals("new.stop should never fire", 0, second.stops)
    }
}
