package com.halo.app.embodiment

import com.halo.sdk.runtime.embodiment.*
import com.halo.sdk.runtime.wearables.*

data class NativeDeviceStatus(val deviceId: String, val protocol: String, val snapshot: NativeSnapshot,
    val supportedActions: Set<NativeAction>)
interface NativeDeviceStatusAccess {
    suspend fun get(deviceId: String): NativeDeviceStatus?
    suspend fun refresh(deviceId: String, action: NativeAction, sourceVersion: String,
        requestAllowed: () -> Boolean = { true }): NativeDeviceStatus?
}

/** Exact saved session only: never scans, creates sessions, pairs, or connects. */
class NativeDeviceStatusActions(private val registry: DeviceRegistry, private val sessions: DeviceSessionManager,
    private val canAccess: () -> Boolean) : NativeDeviceStatusAccess {
    private suspend fun resolve(id: String): NativeWearableSession? {
        if (!canAccess()) return null
        val record = registry.getDevice(id) ?: return null
        val session = sessions.getSession(id) as? NativeWearableSession ?: return null
        return session.takeIf { it.identity.deviceId == record.identity.deviceId && it.identity.deviceType == record.identity.deviceType &&
            it.driver.deviceId == id && canAccess() }
    }
    override suspend fun get(deviceId: String): NativeDeviceStatus? {
        val session = resolve(deviceId) ?: return null
        return snapshot(session)
    }
    override suspend fun refresh(deviceId: String, action: NativeAction, sourceVersion: String,
        requestAllowed: () -> Boolean): NativeDeviceStatus? {
        require(action in setOf(NativeAction.BATTERY, NativeAction.DEVICE_INFO))
        val session = resolve(deviceId) ?: return null
        val driver = session.driver
        if (action !in driver.supportedActions || driver.state.value.stage != NativeStage.READY ||
            sourceVersion != driver.state.value.sourceVersion || !requestAllowed()) return null
        // The driver repeats these checks after acquiring its workflow lease.
        driver.execute(action, sourceVersion) { canAccess() && requestAllowed() && sessions.getSession(deviceId) === session }
        if (resolve(deviceId) !== session || !requestAllowed()) return null
        return snapshot(session)
    }
    private fun snapshot(session: NativeWearableSession) = NativeDeviceStatus(session.identity.deviceId,
        session.driver.protocolId, session.driver.state.value, session.driver.supportedActions)
}
