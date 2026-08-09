package com.halo.drivers.heycyan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureScenarioTest {
    @Test
    fun scenariosExposeStableIdsAndHardwareInstructions() {
        assertEquals(
            listOf("boot-001", "button-single-001", "button-double-001", "button-long-001", "battery-001", "reconnect-001"),
            CaptureScenario.entries.map { it.id }
        )
        assertTrue(CaptureScenario.BOOT.instructions.contains("power"))
        assertTrue(CaptureScenario.BUTTON_SINGLE.instructions.contains("single"))
        assertTrue(CaptureScenario.BATTERY.instructions.contains("charging"))
    }

    @Test
    fun scenarioIdsRoundTripWithoutGuessing() {
        CaptureScenario.entries.forEach { scenario ->
            assertEquals(scenario, CaptureScenario.fromId(scenario.id))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownScenarioIdIsRejected() {
        CaptureScenario.fromId("not-a-real-scenario")
    }
}
