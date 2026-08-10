package com.halo.drivers.heycyan.protocol

import java.util.UUID

data class DecodedEvent(
    val event: HeyCyanEvent,
    val rawPayload: ByteArray,
    val timestamp: Long,
    val characteristicUuid: UUID?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedEvent) return false
        return event == other.event &&
                rawPayload.contentEquals(other.rawPayload) &&
                timestamp == other.timestamp &&
                characteristicUuid == other.characteristicUuid
    }

    override fun hashCode(): Int {
        var result = event.hashCode()
        result = 31 * result + rawPayload.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (characteristicUuid?.hashCode() ?: 0)
        return result
    }
}
