package com.halo.drivers.heycyan.adapter

import com.halo.drivers.heycyan.protocol.HeyCyanConnectionState
import com.halo.drivers.heycyan.protocol.HeyCyanDeviceState
import com.halo.sdk.host.DeviceContext

fun HeyCyanDeviceState.toDeviceContext(): DeviceContext {
    return DeviceContext(
        batteryPercent = battery?.level,
        wearing = false,
        charging = battery?.charging ?: false,
        connected = connection == HeyCyanConnectionState.READY,
    )
}
