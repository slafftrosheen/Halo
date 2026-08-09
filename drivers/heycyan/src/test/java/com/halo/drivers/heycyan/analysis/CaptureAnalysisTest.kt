package com.halo.drivers.heycyan.analysis

import com.halo.drivers.heycyan.codec.BleCapture
import com.halo.drivers.heycyan.codec.BlePacket
import com.halo.drivers.heycyan.codec.PacketDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CaptureAnalysisTest {
    private val characteristic = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `summarizes raw packet counts and duration without protocol interpretation`() {
        val capture = BleCapture(
            captureId = "boot-001",
            startedAt = 100L,
            endedAt = 450L,
            packets = listOf(
                BlePacket(0L, 120L, PacketDirection.RX, characteristic, byteArrayOf(0x01)),
                BlePacket(1L, 300L, PacketDirection.TX, characteristic, byteArrayOf(0x02))
            )
        )

        val analysis = CaptureAnalyzer.summarize(capture)

        assertEquals("boot-001", analysis.captureId)
        assertEquals(2, analysis.packetCount)
        assertEquals(1, analysis.rxCount)
        assertEquals(1, analysis.txCount)
        assertEquals(350L, analysis.durationMillis)
        assertEquals(listOf("00000000-0000-0000-0000-000000000001"), analysis.characteristics.toList())
        assertEquals(listOf("01", "02"), analysis.findings.map { it.payloadHex })
        assertTrue(analysis.findings.all { it.observation.startsWith("Raw ") })
    }

    @Test
    fun `analysis orders findings by recorder packet sequence`() {
        val capture = BleCapture(
            captureId = "ordered-001",
            startedAt = 0L,
            packets = listOf(
                BlePacket(7L, 700L, PacketDirection.RX, characteristic, byteArrayOf(0x07)),
                BlePacket(2L, 200L, PacketDirection.RX, characteristic, byteArrayOf(0x02))
            )
        )

        assertEquals(listOf(2L, 7L), CaptureAnalyzer.summarize(capture).findings.map { it.sequence })
    }
}
