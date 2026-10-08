package com.halo.host.android.device

import android.companion.CompanionDeviceService
import android.companion.AssociationInfo
import android.os.Build
import androidx.annotation.RequiresApi

/** Implemented by the process Application, never a static repository reference. */
interface CompanionPresenceOwner {
    fun onCompanionPresence(address: String, appeared: Boolean)
}

@RequiresApi(Build.VERSION_CODES.S)
class HaloCompanionDeviceService : CompanionDeviceService() {
    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) = dispatch(address, true)
    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) = dispatch(address, false)
    @RequiresApi(33)
    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        associationInfo.deviceMacAddress?.toString()?.let { dispatch(it, true) }
    }
    @RequiresApi(33)
    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        associationInfo.deviceMacAddress?.toString()?.let { dispatch(it, false) }
    }
    @RequiresApi(36)
    override fun onDevicePresenceEvent(event: android.companion.DevicePresenceEvent) {
        val appeared = when (event.event) {
            android.companion.DevicePresenceEvent.EVENT_BLE_APPEARED,
            android.companion.DevicePresenceEvent.EVENT_BT_CONNECTED,
            android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_APPEARED -> true
            android.companion.DevicePresenceEvent.EVENT_BLE_DISAPPEARED,
            android.companion.DevicePresenceEvent.EVENT_BT_DISCONNECTED,
            android.companion.DevicePresenceEvent.EVENT_SELF_MANAGED_DISAPPEARED -> false
            else -> return
        }
        getSystemService(android.companion.CompanionDeviceManager::class.java).myAssociations
            .firstOrNull { it.id == event.associationId }?.deviceMacAddress?.toString()?.let { dispatch(it, appeared) }
    }
    private fun dispatch(address: String, appeared: Boolean) {
        (application as? CompanionPresenceOwner)?.onCompanionPresence(address, appeared)
            ?: error("Application must own the companion presence bridge")
    }
}
