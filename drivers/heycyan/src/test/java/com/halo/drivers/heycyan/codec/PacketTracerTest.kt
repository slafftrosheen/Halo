package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PacketTracerTest {
    @Test
    fun rfcommObservationIsStoredAsRawPacketWithoutProtocolInterpretation() {
        val directory = Files.createTempDirectory("rfcomm-capture").toFile()
        val store = JsonCaptureSessionStore(directory)
        val recorder = CaptureSessionRecorder(store, captureIdFactory = { "rfcomm-001" })
        recorder.start(scenario = "boot-001", captureId = "rfcomm-001")

        recorder.onRfcomm("primary", PacketDirection.RX, byteArrayOf(0x52, 0x91.toByte(), 0x00, 0x00))
        val capture = requireNotNull(recorder.snapshot())

        assertEquals(1, capture.packets.size)
        assertEquals(PacketDirection.RX, capture.packets.single().direction)
        assertEquals(PacketTransport.RFCOMM, capture.packets.single().transport)
        assertEquals("primary", capture.packets.single().transportChannel)
        assertEquals(4, capture.packets.single().payload.size)
    }
}
