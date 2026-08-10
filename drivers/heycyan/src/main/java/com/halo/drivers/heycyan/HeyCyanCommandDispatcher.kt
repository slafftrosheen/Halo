package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.ble.HeyCyanGattClient
import com.halo.drivers.heycyan.codec.PacketTracer
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanCommandCodec
import java.util.UUID

/**
 * Dispatches HeyCyan BLE commands and correlates responses from the event stream.
 *
 * This replaces the broken waitForEvent() pattern in HeyCyanDeviceControllerImpl.
 * The dispatcher:
 * 1. Encodes a semantic command using an evidence-backed codec
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
    private val tracer: PacketTracer,
    private val codec: HeyCyanCommandCodec
) {
    private val mutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Executes a semantic command through the sole evidence-gated write path.
     *
     * A missing protocol definition is a normal blocked state while HeyCyan
     * captures are unavailable. It must never result in guessed bytes being sent.
     */
    suspend fun execute(command: HeyCyanCommand): Result<Unit> = runCatching {
        mutex.lock()
        try {
            val encoded = codec.encode(command).getOrThrow()
            check(encoded.targetCharacteristic == commandCharacteristic) {
                "Evidence-backed command targets ${encoded.targetCharacteristic}, " +
                    "but the configured command characteristic is $commandCharacteristic"
            }
            writePacket(encoded.bytes)
        } finally {
            mutex.unlock()
        }
    }

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
            val command = when (mode) {
                HeyCyanMode.PHOTO -> HeyCyanCommand.TakePhoto
                HeyCyanMode.VIDEO -> HeyCyanCommand.StartVideo
                HeyCyanMode.VIDEO_STOP -> HeyCyanCommand.StopVideo
                HeyCyanMode.AUDIO -> HeyCyanCommand.StartAudio
                HeyCyanMode.AUDIO_STOP -> HeyCyanCommand.StopAudio
                HeyCyanMode.TRANSFER -> HeyCyanCommand.EnableTransfer
                HeyCyanMode.AI_PHOTO -> throw UnsupportedOperationException(
                    "AI Photo is blocked until its complete response protocol is verified"
                )
            }
            writeCommandLocked(command)
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
            writeCommandLocked(HeyCyanCommand.GetBattery)
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
            writeCommandLocked(HeyCyanCommand.GetVersion)
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
            writeCommandLocked(HeyCyanCommand.GetMediaCounts)
            eventStream.awaitMediaCounts(timeoutMillis)
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Sends a query command for MAC address.
     * Response format is not fully verified — returns raw unknown event.
     */
    suspend fun queryMacAddress(timeoutMillis: Long = 5000L): Result<HeyCyanBleEvent> = Result.failure(
        UnsupportedOperationException(
            "Device-reported MAC address is blocked until its response representation is verified"
        )
    )

    /**
     * Sends a set-datetime command. Fire-and-forget (no specific response event).
     */
    suspend fun sendSetDateTime(): Result<Unit> = Result.failure(
        UnsupportedOperationException(
            "Date/time synchronization is blocked until its payload and acknowledgment are verified"
        )
    )

    /**
     * Sends a transfer mode command and awaits WiFi credentials.
     */
    suspend fun sendTransferMode(
        timeoutMillis: Long = 5000L
    ): Result<HeyCyanBleEvent.WiFiCredentials> = runCatching {
        mutex.lock()
        try {
            writeCommandLocked(HeyCyanCommand.EnableTransfer)
            eventStream.awaitWiFiCredentials(timeoutMillis)
        } finally {
            mutex.unlock()
        }
    }

    private fun writeCommandLocked(command: HeyCyanCommand) {
        val encoded = codec.encode(command).getOrThrow()
        check(encoded.targetCharacteristic == commandCharacteristic) {
            "Evidence-backed command targets ${encoded.targetCharacteristic}, " +
                "but the configured command characteristic is $commandCharacteristic"
        }
        writePacket(encoded.bytes)
    }

    private fun writePacket(packet: ByteArray) {
        tracer.onTx(commandCharacteristic, packet)
        val success = gattClient.write(commandCharacteristic, packet)
        check(success) { "GATT write to command characteristic failed" }
    }
}
