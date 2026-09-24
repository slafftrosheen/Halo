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
    private fun dispatch(address: String, appeared: Boolean) {
        (application as? CompanionPresenceOwner)?.onCompanionPresence(address, appeared)
            ?: error("Application must own the companion presence bridge")
    }
}
