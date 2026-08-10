package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanMode

data class EncodedCommand(
    val commandId: Int,
    val payload: ByteArray,
)

class HeyCyanCommandEncoder {

    fun encode(
        command: HeyCyanCommand
    ): Result<EncodedCommand> =
        when (command) {

            HeyCyanCommand.GetVersion ->
                Result.success(
                    EncodedCommand(
                        commandId = 0x10,
                        payload = byteArrayOf()
                    )
                )

            HeyCyanCommand.GetMacAddress ->
                Result.success(
                    EncodedCommand(
                        commandId = 0x11,
                        payload = byteArrayOf()
                    )
                )

            HeyCyanCommand.GetBattery ->
                Result.success(
                    EncodedCommand(
                        commandId = 0x13,
                        payload = byteArrayOf()
                    )
                )

            HeyCyanCommand.GetMediaCounts ->
                Result.success(
                    EncodedCommand(
                        commandId = 0x14,
                        payload = byteArrayOf()
                    )
                )

            is HeyCyanCommand.SetMode ->
                Result.success(
                    EncodedCommand(
                        commandId = 0x01,
                        payload = byteArrayOf(
                            command.mode.value.toByte()
                        )
                    )
                )

            is HeyCyanCommand.SetDateTime ->
                Result.failure(
                    UnsupportedOperationException(
                        "HeyCyan SET_DATETIME payload format is not established"
                    )
                )

            is HeyCyanCommand.EnableTransfer ->
                Result.failure(
                    UnsupportedOperationException(
                        "HeyCyan EnableTransfer payload format is not established"
                    )
                )

            else ->
                Result.failure(
                    UnsupportedOperationException(
                        "HeyCyan command not yet implemented"
                    )
                )
        }
}