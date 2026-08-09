package com.halo.drivers.heycyan.codec

import java.util.UUID

/**
 * A transport-level difference between two captures. A null payload means that
 * the packet exists only on that side of the comparison.
 */
data class PacketDifference(
    val sequence: Long?,
    val direction: PacketDirection,
    val characteristicUuid: UUID,
    val baselinePayload: ByteArray?,
    val actionPayload: ByteArray?
) {
    init {
        require(baselinePayload != null || actionPayload != null) {
            "A packet difference must contain a baseline or action packet"
        }
    }

    fun baselineHex(): String? = baselinePayload?.let(HexCodec::encode)

    fun actionHex(): String? = actionPayload?.let(HexCodec::encode)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PacketDifference) return false
        return sequence == other.sequence &&
            direction == other.direction &&
            characteristicUuid == other.characteristicUuid &&
            baselinePayload.contentEqualsNullable(other.baselinePayload) &&
            actionPayload.contentEqualsNullable(other.actionPayload)
    }

    override fun hashCode(): Int {
        var result = sequence?.hashCode() ?: 0
        result = 31 * result + direction.hashCode()
        result = 31 * result + characteristicUuid.hashCode()
        result = 31 * result + (baselinePayload?.contentHashCode() ?: 0)
        result = 31 * result + (actionPayload?.contentHashCode() ?: 0)
        return result
    }
}

/** Compares transport packets without interpreting protocol fields. */
interface PacketCorrelation {
    fun compare(baseline: BleCapture, action: BleCapture): List<PacketDifference>
}

/**
 * Aligns packets by direction and characteristic, preserving packet order
 * within each stream. This intentionally does not align by packet sequence:
 * an action can insert packets and shift all subsequent sequence numbers.
 */
class OrderedPacketCorrelation : PacketCorrelation {
    override fun compare(baseline: BleCapture, action: BleCapture): List<PacketDifference> {
        val baselineStreams = baseline.packets.groupBy { StreamKey(it.direction, it.characteristic) }
        val actionStreams = action.packets.groupBy { StreamKey(it.direction, it.characteristic) }
        val streams = (baselineStreams.keys + actionStreams.keys)
            .sortedWith(compareBy<StreamKey>({ it.direction.ordinal }, { it.characteristic.toString() }))

        return streams.flatMap { key ->
            val baselinePackets = baselineStreams[key].orEmpty()
            val actionPackets = actionStreams[key].orEmpty()
            val size = maxOf(baselinePackets.size, actionPackets.size)

            (0 until size).mapNotNull { index ->
                val baselinePacket = baselinePackets.getOrNull(index)
                val actionPacket = actionPackets.getOrNull(index)
                if (baselinePacket?.payload.contentEqualsNullable(actionPacket?.payload) == true) {
                    null
                } else {
                    PacketDifference(
                        sequence = actionPacket?.sequence ?: baselinePacket?.sequence,
                        direction = key.direction,
                        characteristicUuid = key.characteristic,
                        baselinePayload = baselinePacket?.payload?.copyOf(),
                        actionPayload = actionPacket?.payload?.copyOf()
                    )
                }
            }
        }
    }

    private data class StreamKey(
        val direction: PacketDirection,
        val characteristic: UUID
    )
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean = when {
    this === other -> true
    this == null || other == null -> false
    else -> contentEquals(other)
}
