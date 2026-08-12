package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommandDefinition
import com.halo.drivers.heycyan.protocol.ProtocolNotVerifiedException

fun interface HeyCyanChecksum {
    fun calculate(header: Byte, length: Byte, commandId: Byte, payload: ByteArray): Byte
}

class HeyCyanPacketFramer(
    private val checksum: HeyCyanChecksum? = null
) {
    fun frameFromDefinition(
        definition: HeyCyanCommandDefinition
    ): Result<ByteArray> {
        if (checksum == null) {
            return Result.failure(
                ProtocolNotVerifiedException(
                    operation = definition.command.toString(),
                    detail = "Checksum implementation unresolved"
                )
            )
        }

        val commandId = definition.commandId ?: return Result.failure(
            ProtocolNotVerifiedException(
                operation = definition.command.toString(),
                detail = "Command ID unresolved"
            )
        )

        val header: Byte = 0xAA.toByte()
        val totalLength: Byte = (4 + definition.payload.size).toByte()
        val cs = checksum.calculate(header, totalLength, commandId.toByte(), definition.payload)

        val packet = ByteArray(totalLength.toInt())
        packet[0] = header
        packet[1] = totalLength
        packet[2] = commandId.toByte()
        definition.payload.copyInto(packet, destinationOffset = 3)
        packet[packet.size - 1] = cs

        return Result.success(packet)
    }

    fun frame(command: EncodedCommand): Result<ByteArray> {
        return Result.failure(
            ProtocolNotVerifiedException(
                operation = "Command 0x${command.commandId.toString(16)}",
                detail = "HeyCyan outer BLE command framing has not been verified"
            )
        )
    }

    /**
     * Bypasses protocol verification for forensic capture purposes.
     * 
     * DANGER: This is for research only. It assumes the payload is already
     * a valid wire frame OR it wraps it in a simplistic AA header if it looks like SDK data.
     */
    fun frameForForensics(command: EncodedCommand): Result<ByteArray> {
        // If it already looks like a framed packet (AA... or BC...), pass it through
        if (command.payload.isNotEmpty() && (command.payload[0].toInt() and 0xFF) == 0xAA) {
            return Result.success(command.payload)
        }

        // Simplistic framing hypothesis for research
        val totalLength = (4 + command.payload.size).toByte()
        val packet = ByteArray(totalLength.toInt())
        packet[0] = 0xAA.toByte()
        packet[1] = totalLength
        packet[2] = command.commandId.toByte()
        command.payload.copyInto(packet, destinationOffset = 3)
        packet[packet.size - 1] = 0x00.toByte() // Null checksum hypothesis

        return Result.success(packet)
    }
}
