package com.halo.drivers.heycyan.transport.rfcomm

import kotlinx.coroutines.flow.Flow

/**
 * Connection states for RFCOMM control transport.
 */
enum class RfcommConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
    FAILED,
}

/**
 * Remote Bluetooth RFCOMM device descriptor.
 */
data class HeyCyanRfcommDevice(
    val address: String,
    val name: String?,
)

/**
 * Transport-independent interface for HeyCyan RFCOMM control protocol operations.
 *
 * Exposes socket connection lifecycle and raw byte I/O streams without referencing
 * Android SDK types (BluetoothSocket, BluetoothDevice, Context, etc.).
 */
interface HeyCyanRfcommTransport {

    /** Current connection lifecycle state. */
    val state: Flow<RfcommConnectionState>

    /** Connect to a remote RFCOMM device. */
    suspend fun connect(device: HeyCyanRfcommDevice): Result<Unit>

    /** Disconnect from the RFCOMM channel. */
    suspend fun disconnect()

    /** Transmit raw byte frame over RFCOMM channel. */
    suspend fun write(bytes: ByteArray): Result<Unit>

    /** Incoming raw byte stream from RFCOMM channel. */
    val incoming: Flow<ByteArray>
}

/**
 * Source-derived RFCOMM constants for HeyCyan SPP communication.
 */
object HeyCyanRfcommConstants {
    /**
     * Standard Serial Port Profile (SPP) UUID used by CyanBridge for RFCOMM control sockets.
     * Sourced from CyanBridge HeyCyanRfcommChannel.PRIMARY.
     */
    val SERVICE_UUID: java.util.UUID =
        java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    /** Frame header prefix for MemoMind RFCOMM packets: 0xFA 0x00 */
    const val FRAME_HEADER_0 = 0xFA.toByte()
    const val FRAME_HEADER_1 = 0x00.toByte()
}
