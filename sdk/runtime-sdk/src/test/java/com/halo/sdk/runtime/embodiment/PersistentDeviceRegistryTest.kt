package com.halo.sdk.runtime.embodiment

import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersistentDeviceRegistryTest {
    private class FakeStorage : Storage {
        private val values = mutableMapOf<String, Any?>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }

    private fun record(id: String = "AA:BB:CC:DD:EE:FF", autoConnect: Boolean = true) = DeviceRecord(
        identity = DeviceIdentity(id, "HeyCyan", "Test Glasses"),
        autoConnect = autoConnect,
        firmwareVersion = "1.2.3",
        lastSeenMs = 1234L
    )

    @Test
    fun saveAndReloadPreservesDevice() = runTest {
        val storage = FakeStorage()
        PersistentDeviceRegistry(storage).saveDevice(record())

        val result = PersistentDeviceRegistry(storage).getDevice("AA:BB:CC:DD:EE:FF")

        assertNotNull(result)
        assertEquals("Test Glasses", result!!.identity.displayName)
        assertEquals("HeyCyan", result.identity.deviceType)
        assertEquals("1.2.3", result.firmwareVersion)
        assertEquals(1234L, result.lastSeenMs)
        assertTrue(result.autoConnect)
    }

    @Test
    fun saveUpdatesAutoConnectWithoutCreatingDuplicate() = runTest {
        val storage = FakeStorage()
        val registry = PersistentDeviceRegistry(storage)
        registry.saveDevice(record())
        registry.saveDevice(record(autoConnect = false))

        val devices = registry.getSavedDevices()

        assertEquals(1, devices.size)
        assertFalse(devices.single().autoConnect)
    }

    @Test
    fun removeDeletesDeviceAndItsRecord() = runTest {
        val storage = FakeStorage()
        val registry = PersistentDeviceRegistry(storage)
        registry.saveDevice(record())

        registry.removeDevice("AA:BB:CC:DD:EE:FF")

        assertEquals(emptyList<DeviceRecord>(), registry.getSavedDevices())
        assertEquals(null, registry.getDevice("AA:BB:CC:DD:EE:FF"))
    }
}
