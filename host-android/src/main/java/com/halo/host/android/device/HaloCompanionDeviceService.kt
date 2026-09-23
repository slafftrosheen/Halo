package com.halo.host.android.device

import android.companion.CompanionDeviceService
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.S)
class HaloCompanionDeviceService : CompanionDeviceService() {

    @Deprecated("Deprecated in Java")
    override fun onDeviceAppeared(address: String) {
        Log.i("HaloCompanion", "Device appeared: $address")
        // Dispatch to DevicePresenceRepository...
    }

    @Deprecated("Deprecated in Java")
    override fun onDeviceDisappeared(address: String) {
        Log.i("HaloCompanion", "Device disappeared: $address")
        // Dispatch to DevicePresenceRepository...
    }
}
