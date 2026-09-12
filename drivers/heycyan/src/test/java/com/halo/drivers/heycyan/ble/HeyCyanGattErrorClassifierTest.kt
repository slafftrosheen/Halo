package com.halo.drivers.heycyan.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeyCyanGattErrorClassifierTest {
    @Test
    fun `status 147 is classified as transient`() {
        assertTrue(HeyCyanGattErrorClassifier.isTransient(147))
        assertEquals("TRANSIENT_GATT_147", HeyCyanGattErrorClassifier.describe(147))
    }

    @Test
    fun `unknown status remains non-transient`() {
        assertEquals(false, HeyCyanGattErrorClassifier.isTransient(133))
        assertEquals("GATT_STATUS_133", HeyCyanGattErrorClassifier.describe(133))
    }
}
