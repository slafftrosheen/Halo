package com.halo.drivers.heycyan.ble

sealed interface GattConnectionFailure {
    data object Timeout : GattConnectionFailure
    data object DeviceNotFound : GattConnectionFailure
    data object BluetoothDisabled : GattConnectionFailure
    data object PermissionMissing : GattConnectionFailure
    data class Platform(val status: Int) : GattConnectionFailure
    data class ServiceDiscovery(val reason: String) : GattConnectionFailure
}
