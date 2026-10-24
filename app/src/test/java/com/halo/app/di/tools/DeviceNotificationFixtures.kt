package com.halo.app.di.tools

import com.halo.app.embodiment.SavedDeviceActions
import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.embodiment.*
import kotlinx.coroutines.flow.MutableStateFlow

internal class SettingsMemoryStorage : Storage {
    private val values = mutableMapOf<String, Any?>()
    override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
    override fun putString(key: String, value: String?) { values[key] = value }
    override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
    override fun putInt(key: String, value: Int) { values[key] = value }
    override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
    override fun putBoolean(key: String, value: Boolean) { values[key] = value }
    override fun remove(key: String) { values.remove(key) }
    override fun clear() = values.clear()
}
internal class SettingsDeviceSession(override val identity: DeviceIdentity) : EmbodimentSession {
    override val state = MutableStateFlow(SessionState.DISCONNECTED)
    var connects = 0
    var failConnect = false
    var disconnects = 0
    override suspend fun connect() { connects++; if (failConnect) throw java.io.IOException("Transport outcome unknown") }
    override suspend fun disconnect() { disconnects++ }
    override suspend fun dispatch(intent: EmbodimentIntent) = Unit
    override suspend fun dispose() = Unit
}
internal class DeviceSettingsFixture {
    val registry = InMemoryDeviceRegistry()
    val sessions = DeviceSessionManager(registry)
    val storage = SettingsMemoryStorage()
    val retried = mutableListOf<String>()
    val owner = SavedDeviceActions(registry, sessions, storage) { id -> retried += id; true }
    suspend fun save(id: String = "AA:01", auto: Boolean = true): SettingsDeviceSession {
        val identity = DeviceIdentity(deviceId = id, displayName = "Saved glasses $id", deviceType = "HEYCYAN_GLASSES")
        registry.saveDevice(DeviceRecord(identity, autoConnect = auto))
        return SettingsDeviceSession(identity).also(sessions::registerSession)
    }
}
