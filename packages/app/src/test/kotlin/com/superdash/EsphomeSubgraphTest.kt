package com.superdash

import com.superdash.esphome.EsphomeBindings
import org.junit.Test

class EsphomeSubgraphTest {
    @Test
    fun `bindings property is typed as EsphomeBindings`() {
        // Smoke: construction wiring is exercised in AppGraphSmokeTest under Robolectric.
        // This test asserts that the type is correct and compiles.
        val ref: (EsphomeSubgraph) -> EsphomeBindings = EsphomeSubgraph::bindings
        @Suppress("KotlinConstantConditions")
        check(ref != null)
    }
}
