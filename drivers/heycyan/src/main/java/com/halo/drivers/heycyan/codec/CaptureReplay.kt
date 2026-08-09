package com.halo.drivers.heycyan.codec

import kotlinx.coroutines.delay

data class ReplayedCaptureEvent(
    val event: CaptureEvent,
    val packet: BlePacket?
)

/**
 * Replays a saved transport timeline without opening Bluetooth or decoding
 * protocol payloads. The callback receives metadata events and their packet,
 * when the event references one.
 */
class CaptureReplayPlayer {
    suspend fun replay(
        capture: BleCapture,
        fastForward: Boolean = false,
        onEvent: suspend (ReplayedCaptureEvent) -> Unit
    ) {
        var previousTimestamp: Long? = null

        capture.timeline
            .sortedBy { it.sequence }
            .forEach { event ->
                if (!fastForward) {
                    val previous = previousTimestamp
                    val elapsed = if (previous == null) 0L else event.timestamp - previous
                    if (elapsed > 0L) delay(elapsed)
                }

                val packet = event.packetIndex?.let { index -> capture.packets.getOrNull(index) }
                onEvent(ReplayedCaptureEvent(event, packet))
                previousTimestamp = event.timestamp
            }
    }

    suspend fun replay(
        file: java.io.File,
        store: CaptureSessionStore,
        fastForward: Boolean = false,
        onEvent: suspend (ReplayedCaptureEvent) -> Unit
    ) = replay(store.load(file), fastForward, onEvent)
}

data class PacketTimelineRow(
    val sequence: Long,
    val packetSequence: Long,
    val timestamp: Long,
    val relativeTimestamp: Long,
    val direction: PacketDirection,
    val characteristic: String,
    val payload: ByteArray,
    val hexPayload: String
) {
    val byteCount: Int get() = payload.size
}

/** Builds an inspection-friendly packet timeline while retaining raw bytes. */
object PacketTimeline {
    fun from(capture: BleCapture): List<PacketTimelineRow> {
        val firstTimestamp = capture.packets.minOfOrNull { it.timestamp } ?: return emptyList()

        return capture.timeline
            .sortedBy { it.sequence }
            .filter { it.kind == CaptureEventKind.PACKET }
            .mapNotNull { event ->
                val packet = event.packetIndex?.let(capture.packets::getOrNull) ?: return@mapNotNull null
                PacketTimelineRow(
                    sequence = event.sequence,
                    packetSequence = packet.sequence,
                    timestamp = packet.timestamp,
                    relativeTimestamp = packet.timestamp - firstTimestamp,
                    direction = packet.direction,
                    characteristic = packet.characteristic.toString(),
                    payload = packet.payload.copyOf(),
                    hexPayload = HexCodec.encode(packet.payload)
                )
            }
    }
}

/**
 * Produces a conventional offset/hex/ascii dump for a captured payload.
 * The method is intentionally presentation-only and has no protocol semantics.
 */
object HexViewer {
    fun format(bytes: ByteArray, bytesPerLine: Int = 16): String {
        require(bytesPerLine > 0) { "Bytes per line must be positive" }
        if (bytes.isEmpty()) return ""

        return bytes.asList()
            .chunked(bytesPerLine)
            .mapIndexed { lineIndex, line ->
                val offset = lineIndex * bytesPerLine
                val hex = line.joinToString(" ") { byte ->
                    "%02X".format(byte.toInt() and 0xFF)
                }.padEnd(bytesPerLine * 3 - 1)
                val ascii = line.joinToString("") { byte ->
                    val value = byte.toInt() and 0xFF
                    if (value in 0x20..0x7E) value.toChar().toString() else "."
                }
                "%08X  %s  |%s|".format(offset, hex, ascii)
            }
            .joinToString("\n")
    }
}