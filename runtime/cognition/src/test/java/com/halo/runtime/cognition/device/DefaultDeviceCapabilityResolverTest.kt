package com.halo.runtime.cognition.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDeviceCapabilityResolverTest {
    private val resolver = DefaultDeviceCapabilityResolver()

    @Test
    fun `prefer same physical device when capability is available`() {
        val heyCyan = RuntimeDevice(
            id = "heycyan_123",
            kind = "wearable",
            displayName = "HeyCyan Glasses",
            connected = true,
            capabilities = setOf(DeviceCapability.MediaCapture(), DeviceCapability.AudioInput())
        )
        val phone = RuntimeDevice(
            id = "phone_local",
            kind = "phone",
            displayName = "Phone",
            connected = true,
            capabilities = setOf(DeviceCapability.MediaCapture(), DeviceCapability.Display())
        )

        val resolution = resolver.resolve(
            requirement = CapabilityRequirement("MediaCapture", preferredDeviceId = "heycyan_123"),
            availableDevices = listOf(phone, heyCyan)
        )

        assertTrue(resolution.matchedPreferred)
        assertEquals("heycyan_123", resolution.selectedDevice?.id)
        assertTrue(resolution.capability is DeviceCapability.MediaCapture)
    }

    @Test
    fun `fallback to available device when preferred device lacks capability`() {
        val heyCyan = RuntimeDevice(
            id = "heycyan_123",
            kind = "wearable",
            displayName = "HeyCyan Glasses",
            connected = true,
            capabilities = setOf(DeviceCapability.AudioInput())
        )
        val phone = RuntimeDevice(
            id = "phone_local",
            kind = "phone",
            displayName = "Phone",
            connected = true,
            capabilities = setOf(DeviceCapability.Display())
        )

        val resolution = resolver.resolve(
            requirement = CapabilityRequirement("Display", preferredDeviceId = "heycyan_123"),
            availableDevices = listOf(heyCyan, phone)
        )

        assertFalse(resolution.matchedPreferred)
        assertEquals("phone_local", resolution.selectedDevice?.id)
        assertTrue(resolution.capability is DeviceCapability.Display)
    }

    @Test
    fun `unresolvable capability returns null`() {
        val phone = RuntimeDevice(
            id = "phone_local",
            kind = "phone",
            displayName = "Phone",
            connected = true,
            capabilities = setOf(DeviceCapability.Display())
        )

        val resolution = resolver.resolve(
            requirement = CapabilityRequirement("Mobility"),
            availableDevices = listOf(phone)
        )

        assertNull(resolution.selectedDevice)
        assertNull(resolution.capability)
    }
}
