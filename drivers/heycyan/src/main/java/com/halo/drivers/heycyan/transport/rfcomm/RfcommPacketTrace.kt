package com.halo.drivers.heycyan.transport.rfcomm

import com.halo.drivers.heycyan.codec.PacketDirection

/**
 * Transport kind discriminator for capture logging.
 */
enum class TransportKind {
    BLE_GATT,
    RFCOMM,
    WIFI_HTTP,
}

/**
 * Forensic packet trace entry for RFCOMM communications.
 * Raw bytes are formatted as uppercase hex strings.
 */
data class RfcommPacketTrace(
    val timestamp: Long,
    val direction: PacketDirection,
    val bytes: ByteArray,
) {
    val hexString: String
        get() = bytes.joinToString("") { "%02X".format(it) }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RfcommPacketTrace) return false
        return timestamp == other.timestamp &&
            direction == other.direction &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
