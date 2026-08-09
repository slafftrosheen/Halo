package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanCommand
import java.util.UUID

/**
 * Responsible for translating HeyCyanCommands into raw BLE byte arrays.
 */
data class EncodedHeyCyanCommand(
    val characteristic: UUID,
    val payload: ByteArray,
    val evidence: List<PacketEvidenceReference>
) {
    init {
        require(evidence.isNotEmpty()) { "Encoded command requires capture evidence" }
    }

    fun copyPayload(): ByteArray = payload.copyOf()
}

class HeyCyanCommandEncoder(
    definitions: List<Definition> = emptyList()
) {
    private val definitions = definitions.toList().also { entries ->
        require(entries.map { it.command }.distinct().size == entries.size) {
            "Command definitions must be unique"
        }
        entries.forEach { definition ->
            require(definition.evidence.isNotEmpty()) {
                "Command ${definition.command} requires capture evidence"
            }
        }
    }

    fun encode(command: HeyCyanCommand): ByteArray = encodeWithTarget(command).copyPayload()

    fun encodeWithTarget(command: HeyCyanCommand): EncodedHeyCyanCommand {
        val definition = definitions.firstOrNull { it.command == command }
            ?: throw UnsupportedOperationException(
                "No evidence-backed encoding is available for ${command::class.simpleName}"
            )
        return EncodedHeyCyanCommand(
            characteristic = definition.characteristic,
            payload = definition.payload.copyOf(),
            evidence = definition.evidence.toList()
        )
    }

    data class Definition(
        val command: HeyCyanCommand,
        val characteristic: UUID,
        val payload: ByteArray,
        val evidence: List<PacketEvidenceReference>
    ) {
        init {
            require(payload.isNotEmpty()) { "Command payload must not be empty" }
        }
    }
}
