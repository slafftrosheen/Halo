package com.halo.drivers.heycyan.codec

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.file.Files

class CaptureSessionAbortTest {
    @Test
    fun abortDiscardsPartialCaptureAndAllowsSameIdToBeRetried() {
        val directory = Files.createTempDirectory("heycyan-abort").toFile()
        val store = JsonCaptureSessionStore(directory)
        val recorder = CaptureSessionRecorder(store)

        recorder.start(scenario = "boot-001", captureId = "boot-001")
        recorder.abort()

        assertNull(recorder.snapshot())
        assertFalse(store.listCaptures().any { it.name == "heycyan-boot-001.json" })

        recorder.start(scenario = "boot-001", captureId = "boot-001")
        val saved = recorder.stop()
        assertNotNull(saved)
        assertNotNull(store.load(requireNotNull(saved)).endedAt)
    }

    @Test
    fun stopReportsDuplicateCaptureAsFailureWithoutLeavingRecorderActive() {
        val directory = Files.createTempDirectory("heycyan-duplicate-stop").toFile()
        val store = JsonCaptureSessionStore(directory)
        val recorder = CaptureSessionRecorder(store)

        recorder.start(scenario = "boot-001", captureId = "boot-001")
        store.save(
            BleCapture(
                captureId = "boot-001",
                startedAt = 999L,
                scenario = "boot-001"
            )
        )

        val failure = runCatching { recorder.stop() }.exceptionOrNull()

        assertNotNull(failure)
        assertNull(recorder.snapshot())
    }
}
