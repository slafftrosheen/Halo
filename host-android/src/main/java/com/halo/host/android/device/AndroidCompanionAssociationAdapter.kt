package com.halo.host.android.device

import android.companion.AssociationRequest
import android.companion.CompanionDeviceManager
import android.companion.ObservingDevicePresenceRequest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler

/** Explicit association only. Android owns discovery and presence; vendor drivers own protocol. */
class AndroidCompanionAssociationAdapter(context: Context) {
    private val available = context.packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)
    private val manager = context.getSystemService(CompanionDeviceManager::class.java)
    fun associate(request: AssociationRequest, callback: CompanionDeviceManager.Callback, handler: Handler?) {
        check(available) { "Companion association unavailable" }
        manager.associate(request, callback, handler)
    }
    fun observe(associationId: Int?, exactAddress: String): Boolean {
        if (!available || Build.VERSION.SDK_INT < 31) return false
        if (Build.VERSION.SDK_INT >= 36 && associationId != null) {
            check(manager.myAssociations.any { it.id == associationId && it.deviceMacAddress?.toString().equals(exactAddress, true) })
            manager.startObservingDevicePresence(ObservingDevicePresenceRequest.Builder().setAssociationId(associationId).build())
        } else {
            @Suppress("DEPRECATION")
            manager.startObservingDevicePresence(exactAddress)
        }
        return true
    }
}
