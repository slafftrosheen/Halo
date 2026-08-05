package com.halo.sdk.driver

sealed interface DriverState {
    data object Discovered : DriverState
    data object Pairing : DriverState
    data object Connected : DriverState
    data object Ready : DriverState
    data object Disconnected : DriverState
    data class Error(val reason: String) : DriverState
}
