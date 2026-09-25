package com.halo.drivers.heycyan.capability

import com.halo.drivers.heycyan.CapabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HeyCyanCapabilityCatalogTest {

    @Test
    fun liveAudioReflectsPhysicalValidation() {
        val descriptor = HeyCyanCapabilityCatalog.find("audio.live_input")
        assertNotNull(descriptor)
        assertEquals(CapabilityStatus.IMPLEMENTED, descriptor?.status)
    }

    @Test
    fun aiPhotoIsAvailableWithHardwareRevalidation() {
        val descriptor = HeyCyanCapabilityCatalog.find("camera.ai_photo")
        assertNotNull(descriptor)
        assertEquals(CapabilityStatus.IMPLEMENTED_WITH_LIMITATION, descriptor?.status)
    }

    @Test
    fun photoIsReferenceSupported() {
        val descriptor = HeyCyanCapabilityCatalog.find("camera.photo")
        assertNotNull(descriptor)
        assertEquals(CapabilityStatus.IMPLEMENTED_WITH_LIMITATION, descriptor?.status)
    }

    @Test
    fun videoIsReferenceSupported() {
        val descriptor = HeyCyanCapabilityCatalog.find("video.record")
        assertNotNull(descriptor)
        assertEquals(CapabilityStatus.IMPLEMENTED_WITH_LIMITATION, descriptor?.status)
    }

    @Test
    fun recordedAudioIsReferenceSupported() {
        val descriptor = HeyCyanCapabilityCatalog.find("audio.record")
        assertNotNull(descriptor)
        assertEquals(CapabilityStatus.IMPLEMENTED_WITH_LIMITATION, descriptor?.status)
    }

    @Test
    fun transferModeIsReferenceSupported() {
        val descriptor = HeyCyanCapabilityCatalog.find("wifi.transfer")
        assertNotNull(descriptor)
        assertEquals(CapabilityStatus.IMPLEMENTED, descriptor?.status)
    }
}
