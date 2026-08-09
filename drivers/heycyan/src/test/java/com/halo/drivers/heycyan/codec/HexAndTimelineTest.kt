package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class HexAndTimelineTest {
    @Test
    fun hexCodecRoundTripsAllByteValues() {
        val bytes = byteArrayOf(0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte())

        assertEquals("00017F80FF", HexCodec.encode(bytes))
        assertTrue(bytes.contentEquals(HexCodec.decode("00017f80ff")))
    }

    @Test
    fun hexViewerShowsOffsetBytesAndPrintableAscii() {
        val rendered = HexViewer.format("Halo\u0000!".toByteArray(), bytesPerLine = 4)

        assertEquals(
            "00000000  48 61 6C 6F  |Halo|\n00000004  00 21        |.!|",
            rendered
        )
    }

    @Test
    fun packetTimelineUsesCaptureEventOrderAndRelativePacketTime() {
        val characteristic = UUID.fromString("00000000-0000-0000-0000-000000000001")
        val capture = BleCapture(
            captureId = "timeline-001",
            startedAt = 100L,
            packets = listOf(
                BlePacket(sequence = 7L, timestamp = 120L, direction = PacketDirection.RX, characteristic = characteristic, payload = byteArrayOf(0x02)),
                BlePacket(sequence = 3L, timestamp = 110L, direction = PacketDirection.TX, characteristic = characteristic, payload = byteArrayOf(0x01))
            ),
            timeline = listOf(
                CaptureEvent(0, 100L, CaptureEventKind.SESSION_STARTED),
                CaptureEvent(1, 120L, CaptureEventKind.PACKET, characteristicUuid = characteristic.toString(), direction = PacketDirection.RX, packetIndex = 0),
                CaptureEvent(2, 110L, CaptureEventKind.PACKET, characteristicUuid = characteristic.toString(), direction = PacketDirection.TX, packetIndex = 1)
            )
        )

        val rows = PacketTimeline.from(capture)

        assertEquals(listOf(PacketDirection.RX, PacketDirection.TX), rows.map { it.direction })
        assertEquals(listOf(7L, 3L), rows.map { it.packetSequence })
        assertEquals(listOf(10L, 0L), rows.map { it.relativeTimestamp })
        assertEquals(listOf("02", "01"), rows.map { it.hexPayload })
    }
}