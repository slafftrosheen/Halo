package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.codec.PacketTracer
import java.util.UUID

/**
 * Verified command encoder for HeyCyan BLE protocol.
 *
 * Mode byte values extracted from CyanBridge `MemoMindPacketEncoder.kt`:
 * - PHOTO = 0x01
 * - VIDEO = 0x02
 * - VIDEO_STOP = 0x03
 * - AUDIO = 0x04
 * - AUDIO_STOP = 0x05
 * - AI_PHOTO = 0x06
 * - TRANSFER = 0x07
 *
 * Query command IDs are INFERRED (not explicit in CyanBridge):
 * - GET_VERSION = 0x10
 * - GET_MAC = 0x11
 * - SET_DATETIME = 0x12
 * - GET_BATTERY = 0x13
 * - GET_MEDIA = 0x14
 *
 * Packet structure (inferred from CyanBridge):
 * [HEADER=0xAA][LENGTH][COMMAND_ID][PAYLOAD...][CHECKSUM]
 *
 * Checksum algorithm: UNKNOWN (needs live capture)
 */
class HeyCyanCommandEncoder(
    private val commandCharacteristic: UUID,
    private val tracer: PacketTracer
) {

    /**
     * Encodes a verified HeyCyan command for transmission.
     *
     * Returns the encoded payload bytes to be written to the command characteristic.
     * The caller is responsible for the GATT write.
     */
    fun encode(command: HeyCyanVerifiedCommand): ByteArray {
        return when (command) {
            is HeyCyanVerifiedCommand.ModeCommand -> encodeModeCommand(command.mode)
            is HeyCyanVerifiedCommand.QueryCommand -> encodeQueryCommand(command)
        }
    }

    private fun encodeModeCommand(mode: HeyCyanMode): ByteArray {
        // Packet: [0xAA][0x04][0x01][mode.byteValue][CHECKSUM]
        // Length = 4 (header + length + cmd + payload)
        // Command ID 0x01 for mode commands
        val payload = byteArrayOf(mode.byteValue)
        return buildPacket(commandId = 0x01.toByte(), payload)
    }

    private fun encodeQueryCommand(command: HeyCyanVerifiedCommand.QueryCommand): ByteArray {
        val commandId: Byte = when (command) {
            HeyCyanVerifiedCommand.QueryCommand.GET_VERSION -> 0x10.toByte()
            HeyCyanVerifiedCommand.QueryCommand.GET_MAC -> 0x11.toByte()
            HeyCyanVerifiedCommand.QueryCommand.SET_DATETIME -> 0x12.toByte()
            HeyCyanVerifiedCommand.QueryCommand.GET_BATTERY -> 0x13.toByte()
            HeyCyanVerifiedCommand.QueryCommand.GET_MEDIA -> 0x14.toByte()
        }
        // Query commands have empty payload
        return buildPacket(commandId = commandId, byteArrayOf())
    }

    private fun buildPacket(commandId: Byte, payload: ByteArray): ByteArray {
        val length = 4 + payload.size  // header + length + cmd_id + payload
        val checksum = calculateChecksum(commandId, payload)

        return byteArrayOf(
            0xAA.toByte(),              // Header
            length.toByte(),            // Length
            commandId,                  // Command ID
            *payload,                   // Payload
            checksum                    // Checksum
        )
    }

    private fun calculateChecksum(commandId: Byte, payload: ByteArray): Byte {
        // Checksum algorithm UNKNOWN - using simple XOR as placeholder
        // Actual algorithm needs live capture validation
        var sum = (0xAA.toInt() + (4 + payload.size) + commandId.toInt()) and 0xFF
        for (b in payload) {
            sum = (sum + (b.toInt() and 0xFF)) and 0xFF
        }
        return sum.toByte()
    }

    /**
     * Writes the encoded command to the command characteristic via GATT.
     */
    suspend fun write(command: HeyCyanVerifiedCommand): Boolean {
        val encoded = encode(command)
        tracer.onTx(commandCharacteristic, encoded)
        // Actual GATT write delegated to GattClient
        return true // Placeholder - real implementation in GattClient
    }
}

/**
 * Sealed class hierarchy for verified commands with evidence references.
 */
sealed class HeyCyanVerifiedCommand(
    val evidence: ProtocolEvidence
) {
    data class ModeCommand(val mode: HeyCyanMode) : HeyCyanVerifiedCommand(
        ProtocolEvidence(
            source = "CyanBridge MemoMindPacketEncoder",
            reference = "Mode byte mapping",
            confidence = Confidence.HIGH
        )
    )

    sealed class QueryCommand : HeyCyanVerifiedCommand(
        ProtocolEvidence(
            source = "Inferred from CyanBridge structure",
            reference = "Command ID allocation pattern",
            confidence = Confidence.MEDIUM
        )
    ) {
        object GET_VERSION : QueryCommand()
        object GET_MAC : QueryCommand()
        object SET_DATETIME : QueryCommand()
        object GET_BATTERY : QueryCommand()
        object GET_MEDIA : QueryCommand()
    }
}

data class ProtocolEvidence(
    val source: String,
    val reference: String,
    val confidence: Confidence
)

enum class Confidence {
    HIGH, MEDIUM, LOW
}