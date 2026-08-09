package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import java.util.UUID

/**
 * Responsible for parsing raw BLE GATT notifications into domain HeyCyanEvents.
 */
class HeyCyanEventDecoder(
    definitions: List<Definition> = emptyList()
) {
    private val definitions = definitions.toList().also { entries ->
        require(entries.map { it.id }.distinct().size == entries.size) {
            "Event definition IDs must be unique"
        }
        entries.forEach { definition ->
            require(definition.evidence.isNotEmpty()) {
                "Event definition ${definition.id} requires capture evidence"
            }
        }
    }

    fun decode(payload: ByteArray): HeyCyanEvent = decode(payload, null, null)

    fun decode(
        payload: ByteArray,
        characteristic: UUID?,
        timestamp: Long?
    ): HeyCyanEvent {
        val immutablePayload = payload.copyOf()
        val definition = definitions.firstOrNull { it.matches(immutablePayload, characteristic) }
        return definition?.decode(immutablePayload, characteristic, timestamp)
            ?: HeyCyanEvent.UnknownEvent(
                payload = immutablePayload,
                characteristic = characteristic,
                timestamp = timestamp
            )
    }

    data class Definition(
        val id: String,
        val evidence: List<PacketEvidenceReference>,
        val matches: (payload: ByteArray, characteristic: UUID?) -> Boolean,
        val decode: (payload: ByteArray, characteristic: UUID?, timestamp: Long?) -> HeyCyanEvent
    ) {
        init {
            require(id.isNotBlank()) { "Event definition ID must not be blank" }
        }
    }
}
