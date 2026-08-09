package com.halo.drivers.heycyan

/**
 * HeyCyan connection state machine.
 *
 * Maps to the SDK's QCState enum:
 * ```
 * QCStateUnbind → QCStateConnecting → QCStateConnected → QCStateReady
 *                     ↓ (disconnect)           ↓ (disconnect)
 *                QCStateDisconnecting → QCStateDisconnected/Unbind
 * ```
 *
 * RECONNECTING is a Halo addition for automatic reconnect after unexpected disconnect.
 */
enum class HeyCyanConnectionState {
    /** No device has been bound/selected. */
    UNBOUND,

    /** BLE connection in progress. */
    CONNECTING,

    /** BLE connected, awaiting service discovery + CCCD. */
    CONNECTED,

    /** Service discovery complete, CCCD subscribed, device ready for commands. */
    READY,

    /** Graceful disconnect in progress. */
    DISCONNECTING,

    /** Disconnected (was previously connected). */
    DISCONNECTED,

    /** Automatic reconnect in progress after unexpected disconnect. */
    RECONNECTING,

    /** Connection failed or unrecoverable error. */
    ERROR
}
