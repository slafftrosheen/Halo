package com.halo.sdk.driver

sealed interface DriverState {
    data object Disconnected : DriverState
    data object Scanning : DriverState
    data object Connecting : DriverState
    data object Connected : DriverState
    data object Ready : DriverState
    data object Disconnecting : DriverState
    data class Error(val cause: Throwable) : DriverState
}
