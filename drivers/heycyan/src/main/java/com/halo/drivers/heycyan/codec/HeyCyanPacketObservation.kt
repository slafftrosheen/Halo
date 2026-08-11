package com.halo.drivers.heycyan.codec

import java.util.UUID

data class HeyCyanPacketObservation(
    val characteristicUuid: UUID,
    val payload: ByteArray,
    val timestampMillis: Long = System.currentTimeMillis()
) {

    val length: Int
        get() = payload.size

    val firstByte: Int?
        get() = payload.firstOrNull()?.toInt()?.and(0xFF)

    val prefix: ByteArray
        get() = payload.take(4).toByteArray()

    fun hex(): String =
        payload.joinToString(" ") { "%02X".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeyCyanPacketObservation) return false
        return characteristicUuid == other.characteristicUuid &&
                payload.contentEquals(other.payload) &&
                timestampMillis == other.timestampMillis
    }

    override fun hashCode(): Int {
        var result = characteristicUuid.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + timestampMillis.hashCode()
        return result
    }
}
