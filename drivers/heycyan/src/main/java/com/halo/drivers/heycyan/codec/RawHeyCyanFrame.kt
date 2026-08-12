package com.halo.drivers.heycyan.codec

import java.util.UUID

/**
 * Raw representation of a HeyCyan protocol frame.
 *
 * Preserves exact bytes, direction, and characteristic UUID for forensic analysis.
 * ByteArray equality and hashCode are implemented to support reliable testing.
 */
data class RawHeyCyanFrame(
    val timestampMillis: Long,
    val direction: PacketDirection,
    val characteristicUuid: UUID,
    val bytes: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RawHeyCyanFrame) return false

        if (timestampMillis != other.timestampMillis) return false
        if (direction != other.direction) return false
        if (characteristicUuid != other.characteristicUuid) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestampMillis.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + characteristicUuid.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    fun hex(): String = bytes.joinToString("") { "%02X".format(it) }
}
