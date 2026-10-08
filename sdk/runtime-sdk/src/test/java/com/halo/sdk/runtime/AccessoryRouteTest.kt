package com.halo.sdk.runtime

import org.junit.Assert.*
import org.junit.Test

class AccessoryRouteTest {
    private fun evidence(id: Int? = 42, type: Int? = 22, address: String? = null) =
        SpeechRouteEvidence(false, type, "Display only", address, androidDeviceId = id)
    @Test fun `USB endpoint without address matches exact id and type`() {
        assertTrue(matchesSpeechRoute(evidence(), 42, 22, null))
        assertFalse(matchesSpeechRoute(evidence(), 43, 22, null))
        assertFalse(matchesSpeechRoute(evidence(), 42, 3, null))
    }
    @Test fun `address never overrides mismatching id or type`() {
        val proof = evidence(address = "exact")
        assertTrue(matchesSpeechRoute(proof, 42, 22, "EXACT"))
        assertFalse(matchesSpeechRoute(proof, 42, 22, "other"))
        assertFalse(matchesSpeechRoute(proof, 43, 22, "exact"))
        assertFalse(matchesSpeechRoute(proof, 42, 3, "exact"))
    }
    @Test fun `empty evidence cannot authorize unmute`() {
        assertFalse(matchesSpeechRoute(evidence(null, null), 42, 22, null))
    }
    @Test fun `phone origin preserves Android output and external origin disallows phone fallback`() {
        assertEquals(SpeechOutputTarget.HostDefault, responseTarget(SpeechInputRoute.Phone))
        val usb = SpeechInputRoute.Device("opaque", 42, "USB", AudioDeviceKind.USB_AUDIO)
        assertEquals(SpeechOutputFallback.DISALLOW_PHONE, fallbackFor(usb, false))
        assertEquals(SpeechOutputFallback.ALLOW_PHONE, fallbackFor(usb, true))
    }
}
