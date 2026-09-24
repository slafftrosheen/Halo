package com.halo.drivers.heycyan.ble

class GattConnectionFailureException(
    val failure: GattConnectionFailure,
    message: String? = null,
    cause: Throwable? = null
) : Exception(message ?: failure.toString(), cause)
