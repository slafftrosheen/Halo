package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class CaptureTransportStateTest {
    @Test
    fun recorderPersistsRfcommChannelOpenAndCloseEvents() {
        val directory = Files.createTempDirectory("heycyan-transport-state").toFile()
        val store = JsonCaptureSessionStore(directory)
        val recorder = CaptureSessionRecorder(store, captureIdFactory = { "transport-state-001" })

        recorder.start(scenario = "boot-001", captureId = "transport-state-001")
        recorder.onRfcommStateChanged("primary", connected = true)
        recorder.onRfcommStateChanged("primary", connected = false)
        val capture = store.load(requireNotNull(recorder.stop()))

        assertEquals(
            listOf(
                CaptureEventKind.SESSION_STARTED,
                CaptureEventKind.RFCOMM_CHANNEL_CONNECTED,
                CaptureEventKind.RFCOMM_CHANNEL_DISCONNECTED,
                CaptureEventKind.SESSION_FINISHED
            ),
            capture.timeline.map { it.kind }
        )
        assertEquals(PacketTransport.RFCOMM, capture.timeline[1].transport)
        assertEquals("primary", capture.timeline[1].transportChannel)
    }
}
