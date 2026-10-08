package com.halo.app.audio

import android.media.AudioDeviceInfo
import com.halo.runtime.cognition.continuity.EndpointPersistence
import org.junit.Assert.*
import org.junit.Test

class AudioAccessoryProjectionTest {
    @Test fun `output only headphones never invent a microphone`() {
        val accessory = projectAudioAccessories(listOf(AudioAccessoryEvidence(42, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            "Headphones", null, false, true, false)), "session").single()
        assertTrue(accessory.canOutput); assertFalse(accessory.canInput); assertFalse(accessory.communicationCapable)
        assertEquals(EndpointPersistence.SESSION_ONLY, accessory.persistence)
    }
    @Test fun `same product name cannot join different devices`() {
        val devices = listOf(
            AudioAccessoryEvidence(41, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Same", null, true, false, true),
            AudioAccessoryEvidence(42, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Same", null, false, true, false))
        assertEquals(2, projectAudioAccessories(devices, "session").size)
        assertNotEquals(projectAudioAccessories(devices, "session").first().id, projectAudioAccessories(devices, "restart").first().id)
    }
    @Test fun `exact address joins Bluetooth profiles and remains private`() {
        val address = "01:23:45:67:89:ab"
        val devices = listOf(
            AudioAccessoryEvidence(41, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "Mic", address, true, true, true),
            AudioAccessoryEvidence(42, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "Media", address.uppercase(), false, true, false))
        val accessory = projectAudioAccessories(devices, "session").single()
        assertEquals(41, accessory.inputDeviceId); assertEquals(42, accessory.outputDeviceId)
        assertTrue(accessory.communicationCapable); assertEquals(EndpointPersistence.DURABLE, accessory.persistence)
        assertEquals(accessory.id, projectAudioAccessories(devices, "restart").single().id)
        assertFalse(accessory.id.contains(address)); assertFalse(accessory.toString().contains(address))
    }
    @Test fun `USB port address is not durable physical identity`() {
        val evidence = listOf(AudioAccessoryEvidence(3, AudioDeviceInfo.TYPE_USB_DEVICE, "USB", "card=1;device=0;", true, true, true))
        val first = projectAudioAccessories(evidence, "first").single()
        assertEquals(EndpointPersistence.SESSION_ONLY, first.persistence)
        assertNotEquals(first.id, projectAudioAccessories(evidence, "restart").single().id)
    }
    @Test fun `USB and wired output are capabilities without Bluetooth claims`() {
        val devices = listOf(AudioAccessoryEvidence(1, AudioDeviceInfo.TYPE_USB_DEVICE, "USB DAC", null, false, true, false),
            AudioAccessoryEvidence(2, AudioDeviceInfo.TYPE_WIRED_HEADSET, "Wired", null, true, true, true))
        val projected = projectAudioAccessories(devices, "session")
        assertFalse(projected[0].canInput); assertTrue(projected[1].canInput)
        assertNotEquals(projected[0].kind, projected[1].kind)
    }
}
