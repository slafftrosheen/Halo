package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.codec.HeyCyanCommand as WireCommand
import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import com.halo.drivers.heycyan.protocol.HeyCyanMode
import java.util.UUID

/**
 * Result of encoding a command into its semantic payload.
 */
data class EncodedCommand(
    val commandId: Int,
    val payload: ByteArray,
    val characteristic: UUID? = null,
    val evidence: List<Any> = emptyList()
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

/**
 * Encodes commands whose semantic operation is established by the
 * HeyCyan SDK / CyanBridge forensic analysis.
 */
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

    fun encodePayload(command: HeyCyanCommand): ByteArray =
        when (command) {
            HeyCyanCommand.TakePhoto ->
                byteArrayOf(MODE_PHOTO)

            HeyCyanCommand.ToggleVideo ->
                byteArrayOf(MODE_VIDEO)

            HeyCyanCommand.PrepareVoiceRouting ->
                byteArrayOf(0x02, 0x01, 0x0B)

            HeyCyanCommand.GetBattery,
            HeyCyanCommand.RequestBattery ->
                byteArrayOf(COMMAND_GET_BATTERY)

            HeyCyanCommand.GetMediaCounts ->
                byteArrayOf(COMMAND_GET_MEDIA_COUNTS)

            HeyCyanCommand.GetVersion,
            HeyCyanCommand.GetDeviceInfo ->
                byteArrayOf(COMMAND_GET_VERSION)

            HeyCyanCommand.GetMacAddress ->
                byteArrayOf(COMMAND_GET_MAC)

            HeyCyanCommand.EnterWifiTransferMode ->
                byteArrayOf(0x02, 0x01, 0x04, 0x01)

            is HeyCyanCommand.SetDateTime ->
                byteArrayOf(COMMAND_SET_DATETIME)

            is HeyCyanCommand.SetMode ->
                byteArrayOf(command.mode.value.toByte())
        }

    /**
     * Encodes a semantic command into its complete verified wire frame byte array (BC 41 ...).
     */
    fun encodeWireFrame(command: HeyCyanCommand): ByteArray =
        when (command) {
            HeyCyanCommand.TakePhoto ->
                WireCommand.photo()

            HeyCyanCommand.ToggleVideo ->
                WireCommand.videoStart()

            HeyCyanCommand.PrepareVoiceRouting ->
                WireCommand.prepareVoiceRouting()

            HeyCyanCommand.EnterWifiTransferMode ->
                WireCommand.wifiTransferMode()

            HeyCyanCommand.GetMediaCounts ->
                WireCommand.mediaCount()

            is HeyCyanCommand.SetMode -> when (command.mode) {
                HeyCyanMode.PHOTO -> WireCommand.photo()
                HeyCyanMode.VIDEO -> WireCommand.videoStart()
                HeyCyanMode.AUDIO -> HeyCyanWireFrameCodec.encode(HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL, byteArrayOf(0x02, 0x01, 0x04)) // Unknown meaning, likely Wi-Fi transfer
                HeyCyanMode.AI_PHOTO -> WireCommand.ai()
                HeyCyanMode.TRANSFER -> HeyCyanWireFrameCodec.encode(HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL, byteArrayOf(0x02, 0x07))
            }

            else -> HeyCyanWireFrameCodec.encode(
                action = HeyCyanWireFrameCodec.ACTION_GLASSES_CONTROL,
                payload = encodePayload(command)
            )
        }

    fun encode(command: HeyCyanCommand): Result<EncodedCommand> = runCatching {
        if (definitions.isNotEmpty()) {
            val def = definitions.firstOrNull { it.command == command }
                ?: throw UnsupportedOperationException("No definition for command $command")
            return@runCatching EncodedCommand(
                commandId = 0x01,
                payload = def.payload.copyOf(),
                characteristic = def.characteristic,
                evidence = def.evidence,
            )
        }

        val payload = encodePayload(command)
        val commandId = when (command) {
            is HeyCyanCommand.TakePhoto, 
            is HeyCyanCommand.ToggleVideo, 
            is HeyCyanCommand.PrepareVoiceRouting, 
            is HeyCyanCommand.EnterWifiTransferMode,
            is HeyCyanCommand.SetMode -> 0x01
            else -> payload[0].toInt() and 0xFF
        }
        val actualPayload = when (command) {
            is HeyCyanCommand.TakePhoto, 
            is HeyCyanCommand.ToggleVideo, 
            is HeyCyanCommand.PrepareVoiceRouting, 
            is HeyCyanCommand.EnterWifiTransferMode,
            is HeyCyanCommand.SetMode -> payload
            else -> byteArrayOf()
        }
        EncodedCommand(commandId, actualPayload)
    }

    fun encodeVerifiedPayload(command: HeyCyanCommand): ByteArray =
        encodePayload(command)

    private companion object {
        const val MODE_PHOTO: Byte = 0x01
        const val MODE_VIDEO: Byte = 0x02
        const val MODE_AUDIO: Byte = 0x04
        const val MODE_AI_PHOTO: Byte = 0x06
        const val MODE_TRANSFER: Byte = 0x07

        const val COMMAND_GET_VERSION: Byte = 0x10
        const val COMMAND_GET_MAC: Byte = 0x11
        const val COMMAND_SET_DATETIME: Byte = 0x12
        const val COMMAND_GET_BATTERY: Byte = 0x13
        const val COMMAND_GET_MEDIA_COUNTS: Byte = 0x14
    }
}
