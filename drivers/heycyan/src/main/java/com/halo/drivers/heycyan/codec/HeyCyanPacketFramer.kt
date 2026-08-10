package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommandDefinition
import com.halo.drivers.heycyan.protocol.ProtocolNotVerifiedException

fun interface HeyCyanChecksum {
    fun calculate(header: Byte, length: Byte, commandId: Byte, payload: ByteArray): Byte
}

class HeyCyanPacketFramer(
    private val checksum: HeyCyanChecksum? = null
) {
    fun frame(
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
        if (checksum == null) {
            return Result.failure(
                ProtocolNotVerifiedException(
                    operation = "Command 0x${command.commandId.toString(16)}",
                    detail = "Checksum implementation unresolved"
                )
            )
        }
        return Result.failure(
            ProtocolNotVerifiedException(
                operation = "Command 0x${command.commandId.toString(16)}",
                detail = "Checksum implementation unresolved"
            )
        )
    }
}