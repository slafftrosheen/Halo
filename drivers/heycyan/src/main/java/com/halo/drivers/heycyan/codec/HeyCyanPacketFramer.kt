package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanCommandDefinition
import com.halo.drivers.heycyan.protocol.ProtocolNotVerifiedException

class HeyCyanPacketFramer {
    fun frameFromDefinition(
        definition: HeyCyanCommandDefinition
    ): Result<ByteArray> {
        val commandId = definition.commandId ?: return Result.failure(
            ProtocolNotVerifiedException(
                operation = definition.command.toString(),
                detail = "Command ID unresolved"
            )
        )

        val action: Byte = when (commandId) {
            0x01, 0x14 -> HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL // 0x41
            0x10, 0x11 -> HeyCyanWireFrameCodec.ACTION_DEVICE_INFO // 0x43
            0x13 -> HeyCyanWireFrameCodec.ACTION_GLASSES_BATTERY // 0x42
            else -> HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL
        }

        val payload = when (commandId) {
            0x14 -> byteArrayOf(0x02, 0x04)
            0x01 -> {
                if (definition.payload.size == 1) {
                    byteArrayOf(0x02, 0x01, definition.payload[0])
                } else {
                    definition.payload
                }
            }
            else -> definition.payload
        }

        return Result.success(HeyCyanWireFrameCodec.encode(action, payload))
    }

    fun frame(command: EncodedCommand): Result<ByteArray> {
        val action: Byte = when (command.commandId) {
            0x01, 0x14 -> HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL // 0x41
            0x10, 0x11 -> HeyCyanWireFrameCodec.ACTION_DEVICE_INFO // 0x43
            0x13 -> HeyCyanWireFrameCodec.ACTION_GLASSES_BATTERY // 0x42
            else -> HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL
        }

        val payload = when (command.commandId) {
            0x14 -> byteArrayOf(0x02, 0x04)
            0x01 -> {
                if (command.payload.size == 1) {
                    byteArrayOf(0x02, 0x01, command.payload[0])
                } else {
                    command.payload
                }
            }
            else -> command.payload
        }

        return Result.success(HeyCyanWireFrameCodec.encode(action, payload))
    }

    /**
     * Bypasses protocol verification for forensic capture purposes.
     */
    fun frameForForensics(command: EncodedCommand): Result<ByteArray> {
        return frame(command)
    }
}
