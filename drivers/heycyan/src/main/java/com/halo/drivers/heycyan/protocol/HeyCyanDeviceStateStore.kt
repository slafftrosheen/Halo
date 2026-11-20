package com.halo.drivers.heycyan.protocol

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class HeyCyanDeviceStateStore(
    private val reducer: HeyCyanDeviceStateReducer =
        HeyCyanDeviceStateReducer(),
) {

    private val _state =
        MutableStateFlow(HeyCyanDeviceState())

    val state: StateFlow<HeyCyanDeviceState> =
        _state.asStateFlow()

    /** Start a new explicitly selected transport without inheriting another device's telemetry. */
    fun beginConnection(controlBleAddress: String) {
        require(controlBleAddress.matches(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")))
        _state.value = HeyCyanDeviceState(
            connection = HeyCyanConnectionState.SCANNING,
            transportIdentity = HeyCyanTransportIdentity(controlBleAddress),
        )
    }

    fun apply(
        event: HeyCyanEvent,
        timestamp: Long = System.currentTimeMillis(),
    ) {
        _state.update { current ->
            reducer.reduce(
                state = current,
                event = event,
                timestamp = timestamp,
            )
        }
    }

    fun updateConnection(connection: HeyCyanConnectionState) {
        _state.update { current ->
            current.copy(connection = connection, transportIdentity = current.transportIdentity.takeUnless {
                connection == HeyCyanConnectionState.DISCONNECTED || connection == HeyCyanConnectionState.ERROR
            })
        }
    }

    fun reset() {
        _state.value = HeyCyanDeviceState()
    }
}
