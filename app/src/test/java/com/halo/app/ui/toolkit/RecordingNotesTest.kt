package com.halo.app.ui.toolkit

import org.junit.Assert.assertEquals
import org.junit.Test

class RecordingNotesTest {
    @Test fun noteKeepsOriginalAndTranslatedCaptionsAtTheSameTimestamp() {
        val captions = listOf(
            HaloRecordCaption(1000, 2000, "Let us begin", 'A'),
            HaloRecordCaption(2400, 3700, "One more point"),
        )
        assertEquals(
            "Decided\nProceed\n\nTranscript\n00:01.0 Speaker A · Let us begin\nSāksim\n00:02.4 · One more point",
            recordingNotesText("Proceed", "", captions, mapOf(1000 to "Sāksim"), "Speaker"),
        )
    }
}
