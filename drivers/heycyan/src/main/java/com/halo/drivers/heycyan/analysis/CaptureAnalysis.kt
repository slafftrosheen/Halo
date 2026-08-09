package com.halo.drivers.heycyan.analysis

import com.halo.drivers.heycyan.codec.AnalysisConfidence
import com.halo.drivers.heycyan.codec.BleCapture
import com.halo.drivers.heycyan.codec.HexCodec
import com.halo.drivers.heycyan.codec.PacketDirection
import java.util.UUID

/**
 * Transport-only summary of a capture. This class deliberately does not assign
 * protocol meanings such as button, battery, or handshake to packet bytes.
 */
data class CaptureAnalysis(
    val captureId: String,
    val packetCount: Int,
    val rxCount: Int,
    val txCount: Int,
    val durationMillis: Long,
    val characteristics: Set<String>,
    val findings: List<PacketFinding>
)

data class PacketFinding(
    val sequence: Long,
    val characteristicUuid: String,
    val direction: PacketDirection,
    val payloadHex: String,
    val observation: String,
    val confidence: AnalysisConfidence
) {
    init {
        require(observation.isNotBlank()) { "Observation must not be blank" }
        require(payloadHex == payloadHex.uppercase()) { "Payload hex must be uppercase" }
        UUID.fromString(characteristicUuid)
    }
}

/** Builds deterministic raw transport summaries for offline review. */
object CaptureAnalyzer {
    fun summarize(capture: BleCapture): CaptureAnalysis {
        val packets = capture.packets.sortedBy { it.sequence }
        val timestamps = packets.map { it.timestamp } + listOfNotNull(capture.startedAt, capture.endedAt)
        val start = timestamps.minOrNull() ?: capture.startedAt
        val end = timestamps.maxOrNull() ?: start

        return CaptureAnalysis(
            captureId = capture.captureId,
            packetCount = packets.size,
            rxCount = packets.count { it.direction == PacketDirection.RX },
            txCount = packets.count { it.direction == PacketDirection.TX },
            durationMillis = (end - start).coerceAtLeast(0L),
            characteristics = packets.map { it.characteristic.toString() }.toSortedSet(),
            findings = packets.map { packet ->
                PacketFinding(
                    sequence = packet.sequence,
                    characteristicUuid = packet.characteristic.toString(),
                    direction = packet.direction,
                    payloadHex = HexCodec.encode(packet.payload),
                    observation = "Raw ${packet.direction} packet observed",
                    confidence = AnalysisConfidence.OBSERVED
                )
            }
        )
    }
}
