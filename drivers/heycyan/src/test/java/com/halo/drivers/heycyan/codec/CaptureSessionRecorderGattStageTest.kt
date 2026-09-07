package com.halo.drivers.heycyan.codec

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CaptureSessionRecorderGattStageTest {

    private class InMemoryStore : CaptureSessionStore {
        var saved: BleCapture? = null

        override fun save(capture: BleCapture): File {
            saved = capture
            return File("capture.json")
        }

        override fun load(file: File): BleCapture = requireNotNull(saved)
    }

    @Test
    fun recordsGattStageWithAttemptAndFailureStatus() {
        val store = InMemoryStore()
        var time = 1000L
        val recorder = CaptureSessionRecorder(
            store = store,
            now = { time++ },
            captureIdFactory = { "capture-$it" }
        )

        recorder.start()
        recorder.onGattStage(
            attemptId = 2L,
            stage = CaptureGattStage.SERVICE_DISCOVERY,
            phase = CaptureStagePhase.FAILED,
            status = 147,
            detail = "status=147"
        )
        recorder.stop()

        val capture = store.saved
        assertNotNull(capture)
        assertEquals(CURRENT_CAPTURE_FORMAT_VERSION, capture!!.schemaVersion)

        val event = capture.timeline.single { it.kind == CaptureEventKind.GATT_STAGE }
        assertEquals(2L, event.attemptId)
        assertEquals(CaptureGattStage.SERVICE_DISCOVERY, event.gattStage)
        assertEquals(CaptureStagePhase.FAILED, event.stagePhase)
        assertEquals(147, event.status)
        assertEquals("status=147", event.detail)
    }
}
