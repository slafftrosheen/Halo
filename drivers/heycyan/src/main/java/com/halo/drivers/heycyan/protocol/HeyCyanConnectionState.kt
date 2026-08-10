package com.halo.drivers.heycyan.protocol

/**
 * HeyCyan device connection state machine.
 *
 * Maps to device protocol state (distinct from driver lifecycle DriverState).
 */
enum class HeyCyanConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    READY,
    DISCONNECTING,
    ERROR,
    UNBOUND,
    RECONNECTING,
}
