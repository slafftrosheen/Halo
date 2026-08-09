package com.halo.drivers.heycyan.protocol

import java.util.UUID

/**
 * Evidence-backed protocol definitions for HeyCyan commands and events.
 * Must be populated with real captures or vendor documentation.
 */
data class HeyCyanProtocolDefinitions(
    val topology: HeyCyanTopologyProfile? = null,
    val commands: List<CommandDefinition> = emptyList(),
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

    fun command(command: HeyCyanCommand): CommandDefinition? =
        commands.firstOrNull { it.command == command }

    data class CommandDefinition(
        val command: HeyCyanCommand,
        val targetCharacteristic: UUID,
        val bytes: ByteArray,
        val evidence: List<ProtocolEvidenceReference>
    ) {
        init {
            require(bytes.isNotEmpty()) { "Command must have bytes" }
            require(evidence.isNotEmpty()) { "Command requires evidence" }
        }
    }

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

/** Evidence references for protocol definitions */
data class ProtocolEvidenceReference(
    val source: String,
    val locator: String,
    val kind: EvidenceKind
) {
    init {
        require(source.isNotBlank()) { "Source must not be blank" }
        require(locator.isNotBlank()) { "Locator must not be blank" }
    }
}

enum class EvidenceKind {
    REAL_DEVICE_CAPTURE,
    AUTHORITATIVE_DOCUMENTATION
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
