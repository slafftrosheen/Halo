package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.ble.HeyCyanGattClient
import com.halo.drivers.heycyan.codec.HeyCyanPacketFramer
import com.halo.drivers.heycyan.codec.PacketTracer
import java.util.UUID

/**
 * Dispatches HeyCyan BLE commands and correlates responses from the event stream.
 *
 * This replaces the broken waitForEvent() pattern in HeyCyanDeviceControllerImpl.
 * The dispatcher:
 * 1. Encodes a command using HeyCyanPacketFramer
 * 2. Writes the framed packet to the GATT command characteristic
 * 3. Awaits the correlated response event from HeyCyanEventStream
 *
 * Thread-safe: commands are serialized through a Mutex to prevent
 * interleaved GATT writes.
 */
class HeyCyanCommandDispatcher(
    private val gattClient: HeyCyanGattClient,
    private val eventStream: HeyCyanEventStream,
    private val commandCharacteristic: UUID,
    private val tracer: PacketTracer
) {
    private val framer = HeyCyanPacketFramer()
    private val mutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Sends a mode command and awaits the ModeChanged response.
     *
     * @param mode The device mode to set
     * @param timeoutMillis Timeout for the response
     * @return The confirmed mode from the device
     */
    suspend fun sendModeCommand(
        mode: HeyCyanMode,
        timeoutMillis: Long = 5000L
    ): Result<HeyCyanBleEvent.ModeChanged> = runCatching {
        mutex.lock()
        try {
            val packet = framer.frame(
                commandId = 0x01.toByte(),
                payload = byteArrayOf(mode.byteValue)
            )
            writePacket(packet)
            eventStream.awaitModeChanged(timeoutMillis)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Sends a query command for battery status.
     */
    suspend fun queryBattery(timeoutMillis: Long = 3000L): Result<HeyCyanBleEvent.Battery> = runCatching {
        mutex.lock()
        try {
            val packet = framer.frame(commandId = 0x13.toByte())
            writePacket(packet)
            eventStream.awaitBattery(timeoutMillis)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Sends a query command for device version info.
     */
    suspend fun queryVersion(timeoutMillis: Long = 5000L): Result<HeyCyanBleEvent.Version> = runCatching {
        mutex.lock()
        try {
            val packet = framer.frame(commandId = 0x10.toByte())
            writePacket(packet)
            eventStream.awaitVersion(timeoutMillis)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Sends a query command for media counts.
     */
    suspend fun queryMediaCounts(timeoutMillis: Long = 3000L): Result<HeyCyanBleEvent.MediaCounts> = runCatching {
        mutex.lock()
        try {
            val packet = framer.frame(commandId = 0x14.toByte())
            writePacket(packet)
            eventStream.awaitMediaCounts(timeoutMillis)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Sends a query command for MAC address.
     * Response format is not fully verified — returns raw unknown event.
     */
    suspend fun queryMacAddress(timeoutMillis: Long = 5000L): Result<HeyCyanBleEvent> = runCatching {
        mutex.lock()
        try {
            val packet = framer.frame(commandId = 0x11.toByte())
            writePacket(packet)
            // MAC response format is TBD — accept any next event within timeout
            eventStream.awaitEvent<HeyCyanBleEvent>(timeoutMillis)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Sends a set-datetime command. Fire-and-forget (no specific response event).
     */
    suspend fun sendSetDateTime(): Result<Unit> = runCatching {
        mutex.lock()
        try {
            val packet = framer.frame(commandId = 0x12.toByte())
            writePacket(packet)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Sends a transfer mode command and awaits WiFi credentials.
     */
    suspend fun sendTransferMode(
        timeoutMillis: Long = 5000L
    ): Result<HeyCyanBleEvent.WiFiCredentials> = runCatching {
        mutex.lock()
        try {
            val packet = framer.frame(
                commandId = 0x01.toByte(),
                payload = byteArrayOf(HeyCyanMode.TRANSFER.byteValue)
            )
            writePacket(packet)
            eventStream.awaitWiFiCredentials(timeoutMillis)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Sends a raw command packet. Used for commands without a known response format.
     */
    suspend fun sendRaw(commandId: Byte, payload: ByteArray = byteArrayOf()): Result<Unit> = runCatching {
        mutex.lock()
        try {
            val packet = framer.frame(commandId, payload)
            writePacket(packet)
        } finally {
            mutex.unlock()
        }
    }

    private fun writePacket(packet: ByteArray) {
        tracer.onTx(commandCharacteristic, packet)
        val success = gattClient.write(commandCharacteristic, packet)
        check(success) { "GATT write to command characteristic failed" }
    }
}
