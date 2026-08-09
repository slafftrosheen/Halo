package com.halo.drivers.heycyan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class HeyCyanTransportPlanTest {
    @Test
    fun discoveryAttemptsRfcommBeforeGattAndDoesNotRequireGatt() {
        val plan = HeyCyanTransportPlan.forMode(HeyCyanConnectionMode.DISCOVERY)

        assertEquals(
            listOf(HeyCyanTransport.RFCOMM, HeyCyanTransport.BLE_GATT),
            plan.attemptOrder
        )
        assertFalse(plan.gattRequired)
    }
}
