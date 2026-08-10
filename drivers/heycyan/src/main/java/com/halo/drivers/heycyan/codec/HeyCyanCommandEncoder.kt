package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import java.util.UUID

data class EncodedCommand(
    val commandId: Int,
    val payload: ByteArray,
    val characteristic: UUID? = null,
    val evidence: List<Any> = emptyList(),
) {
    fun copyPayload(): ByteArray = payload.copyOf()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncodedCommand) return false
        return commandId == other.commandId &&
                payload.contentEquals(other.payload) &&
                characteristic == other.characteristic &&
                evidence == other.evidence
    }

    override fun hashCode(): Int {
        var result = commandId
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + (characteristic?.hashCode() ?: 0)
        result = 31 * result + evidence.hashCode()
        return result
    }
}

class HeyCyanCommandEncoder(
    private val definitions: List<Definition> = emptyList(),
) {

    data class Definition(
        val command: HeyCyanCommand,
        val characteristic: UUID,
        val payload: ByteArray,
        val evidence: List<Any> = emptyList(),
    )

    fun encodeWithTarget(command: HeyCyanCommand): EncodedCommand {
        val def = definitions.firstOrNull { it.command == command }
            ?: throw UnsupportedOperationException("No definition for command $command")
        return EncodedCommand(
            commandId = 0x01,
            payload = def.payload.copyOf(),
            characteristic = def.characteristic,
            evidence = def.evidence,
        )
    }

    fun encode(
        command: HeyCyanCommand
    ): Result<EncodedCommand> {
        if (definitions.isNotEmpty()) {
            val def = definitions.firstOrNull { it.command == command }
                ?: return Result.failure(
                    UnsupportedOperationException("No definition for command $command")
                )
            return Result.success(
                EncodedCommand(
                    commandId = 0x01,
                    payload = def.payload.copyOf(),
                    characteristic = def.characteristic,
                    evidence = def.evidence,
                )
            )
        }

        return when (command) {
            HeyCyanCommand.GetVersion,
            HeyCyanCommand.GetDeviceInfo ->
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
            HeyCyanCommand.RequestBattery ->
                Result.failure(
                    UnsupportedOperationException("HeyCyan RequestBattery is speculative and not implemented")
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
}