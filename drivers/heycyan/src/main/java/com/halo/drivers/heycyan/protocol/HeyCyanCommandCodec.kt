package com.halo.drivers.heycyan.protocol

import java.util.UUID

/**
 * Single evidence-gated codec for HeyCyan BLE protocol.
 * All encoding/decoding requires explicit evidence-backed definitions.
 */
interface HeyCyanCommandCodec {
    /**
     * Encodes a semantic command to bytes for transmission.
     * Fails if no evidence-backed definition exists for the command.
     */
    fun encode(command: HeyCyanCommand): Result<EncodedCommand>

    /**
     * Decodes a raw notification payload to a semantic event.
     * Returns UnknownEvent if no evidence-backed definition matches.
     */
    fun decode(payload: ByteArray, characteristic: UUID, timestamp: Long): HeyCyanEvent
}

/** Encoded command ready for GATT write */
data class EncodedCommand(
    val targetCharacteristic: UUID,
    val bytes: ByteArray
)

/** Default implementation using protocol definitions */
class HeyCyanCommandCodecImpl(
    private val definitions: HeyCyanProtocolDefinitions
) : HeyCyanCommandCodec {

    override fun encode(command: HeyCyanCommand): Result<EncodedCommand> {
        val definition = definitions.command(command)
            ?: return Result.failure(
                ProtocolNotVerifiedException("encode ${command::class.simpleName}")
            )
        return Result.success(
            EncodedCommand(
                targetCharacteristic = definition.targetCharacteristic,
                bytes = definition.copyBytes()
            )
        )
    }

    override fun decode(payload: ByteArray, characteristic: UUID, timestamp: Long): HeyCyanEvent {
        val definition = definitions.events.firstOrNull { it.characteristic == characteristic }
            ?: return HeyCyanEvent.UnknownEvent(payload, characteristic, timestamp, "No event definition for characteristic")

        val matchingDef = definition.matches(payload)
            ?.let { definition }
            ?: return HeyCyanEvent.UnknownEvent(payload, characteristic, timestamp, "No matching event definition")

        return matchingDef.decode(payload, timestamp)
    }
}