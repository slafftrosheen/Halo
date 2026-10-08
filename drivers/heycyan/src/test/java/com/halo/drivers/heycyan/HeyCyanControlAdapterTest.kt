package com.halo.drivers.heycyan
import com.halo.drivers.heycyan.protocol.ButtonType
import com.halo.sdk.host.*
import org.junit.Assert.*
import org.junit.Test
class HeyCyanControlAdapterTest {
    @Test fun allVendorIdentitiesSurviveWithoutPromotingUnverifiedTouch() {
        val delivery = ControlDelivery("opaque", "epoch", "event", ControlOrigin.MANAGED_DEVICE, ControlEvidence.UNKNOWN)
        val events = ButtonType.entries.map { normalizeHeyCyanControl(it, 123, delivery) }
        assertEquals(ButtonType.entries.size, events.map { it.control to it.action }.distinct().size)
        assertEquals(listOf(AccessoryControlAction.SINGLE, AccessoryControlAction.DOUBLE, AccessoryControlAction.LONG), events.take(3).map { it.action })
        assertTrue(events.take(3).all { it.delivery!!.evidence == ControlEvidence.OBSERVED })
        assertEquals(ControlEvidence.UNKNOWN, events.last().delivery!!.evidence)
        assertTrue(events.all { it.timestamp == 123L && it.delivery!!.classified })
    }
}
