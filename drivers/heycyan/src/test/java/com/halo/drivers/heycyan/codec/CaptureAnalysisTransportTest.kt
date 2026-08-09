package com.halo.drivers.heycyan.codec

import com.halo.drivers.heycyan.analysis.CaptureAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class CaptureAnalysisTransportTest {
    private val characteristic = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Test
    fun `packet finding keeps raw payload and explicit observed confidence`() {
        val capture = BleCapture(
            captureId = "boot-001",
            startedAt = 10L,
            endedAt = 20L,
            packets = listOf(
                BlePacket(0L, 12L, PacketDirection.RX, characteristic, byteArrayOf(0x00, 0x7F))
            )
        )

        val finding = CaptureAnalyzer.summarize(capture).findings.single()

        assertEquals("007F", finding.payloadHex)
        assertEquals(AnalysisConfidence.OBSERVED, finding.confidence)
        assertEquals("Raw RX packet observed", finding.observation)
    }

    @Test
    fun `capture analysis does not classify raw packets`() {
        val capture = BleCapture(
            captureId = "button-single-001",
            startedAt = 10L,
            packets = listOf(
                BlePacket(0L, 12L, PacketDirection.RX, characteristic, byteArrayOf(0x03, 0x01))
            )
        )

        val finding = CaptureAnalyzer.summarize(capture).findings.single()

        assertTrue(finding.observation.contains("Raw"))
    }
}
