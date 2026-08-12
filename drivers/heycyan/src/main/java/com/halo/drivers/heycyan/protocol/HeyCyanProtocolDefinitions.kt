package com.halo.drivers.heycyan.protocol

import java.util.UUID

enum class ProtocolDefinitionStatus {
    VERIFIED_FROM_REFERENCE,
    VERIFIED_FROM_CAPTURE,
    VERIFIED_FROM_DECOMPILED_SDK,
    IMPLEMENTED_WITH_LIMITATION,
    BLOCKED,
}

enum class EvidenceKind {
    REAL_DEVICE_CAPTURE,
    AUTHORITATIVE_SOURCE,
}

enum class ProtocolConfidence {
    HIGH,
    MEDIUM,
    LOW,
}

/** Evidence references for protocol definitions */
data class ProtocolEvidenceReference(
    val source: String,
    val locator: String,
    val kind: EvidenceKind,
    val confidence: ProtocolConfidence = ProtocolConfidence.HIGH,
) {
    init {
        require(source.isNotBlank()) { "Source must not be blank" }
        require(locator.isNotBlank()) { "Locator must not be blank" }
    }
}

/**
 * Source-backed command definition for HeyCyan commands.
 * Target characteristic is handled at transport layer.
 */
data class HeyCyanCommandDefinition(
    val command: HeyCyanCommand,
    val commandId: Int?,
    val payload: ByteArray = byteArrayOf(),
    val evidence: List<ProtocolEvidenceReference>,
    val status: ProtocolDefinitionStatus,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeyCyanCommandDefinition) return false
        return command == other.command &&
                commandId == other.commandId &&
                payload.contentEquals(other.payload) &&
                evidence == other.evidence &&
                status == other.status
    }

    override fun hashCode(): Int {
        var result = command.hashCode()
        result = 31 * result + (commandId ?: 0)
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + evidence.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }
}

typealias CommandDefinition = HeyCyanCommandDefinition

/**
 * Evidence-backed protocol definitions collection for HeyCyan.
 */
data class HeyCyanProtocolDefinitions(
    val topology: HeyCyanTopologyProfile? = null,
    val commands: List<HeyCyanCommandDefinition> = emptyList(),
    val events: List<EventDefinition> = emptyList()
) {
    init {
        require(commands.map { it.command }.distinct().size == commands.size) {
            "Each command may have at most one definition"
        }
        require(events.map { it.id }.distinct().size == events.size) {
            "Each event ID must be unique"
        }
    }

    fun command(command: HeyCyanCommand): HeyCyanCommandDefinition? =
        commands.firstOrNull { it.command == command }

    data class EventDefinition(
        val id: String,
        val characteristic: UUID,
        val evidence: List<ProtocolEvidenceReference>,
        val matches: (ByteArray) -> Boolean,
        val decode: (ByteArray, Long) -> HeyCyanEvent
    ) {
        init {
            require(id.isNotBlank()) { "Event ID must not be blank" }
            require(evidence.isNotEmpty()) { "Event requires evidence" }
        }
    }
}

/**
 * Topology profile with verified GATT endpoints
 * Must be populated from real-device capture evidence
 */
data class HeyCyanTopologyProfile(
    val service: UUID,
    val commandCharacteristic: UUID,
    val notificationCharacteristic: UUID,
    val evidence: List<ProtocolEvidenceReference>
) {
    init {
        require(evidence.isNotEmpty()) { "Topology requires evidence" }
    }
}

/**
 * Exception for unsupported operations without evidence
 */
class ProtocolNotVerifiedException(
    val operation: String,
    detail: String = "No evidence-backed definition"
) : UnsupportedOperationException("HeyCyan $operation blocked: $detail")

