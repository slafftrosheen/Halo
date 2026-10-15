package com.halo.app.ui.screens.settings

import com.halo.app.ui.icons.HaloGlyph
import org.junit.Assert.assertEquals
import org.junit.Test

/** The summary cannot claim a probe passed when only availability was observed. */
class SystemCheckSummaryTest {
    private fun item(id: String, status: CheckStatus) =
        SystemCheckItem(id, id, HaloGlyph.DIAGNOSTICS, status, "Recorded status")

    @Test fun countsRecordedAvailabilityAndProbeSuccessWithoutInventingEither() {
        val sections = listOf(
            SystemCheckSection("phone", "Phone", listOf(
                item("voice", CheckStatus.READY),
                item("model", CheckStatus.PASSED),
                item("camera", CheckStatus.NOT_CONFIGURED),
            )),
            SystemCheckSection("providers", "Providers", listOf(
                item("cloud", CheckStatus.FAILED),
                item("local", CheckStatus.RUNNING),
            ))
        )
        assertEquals(SystemCheckTally(good = 2, total = 5), systemCheckTally(sections))
    }

    @Test fun noReportsStayUnknownAndNeverBecomeAllGreen() {
        assertEquals(SystemCheckTally(0, 0), systemCheckTally(emptyList()))
        assertEquals(SystemCheckTally(0, 1), systemCheckTally(
            listOf(SystemCheckSection("p", "Provider", listOf(item("offline", CheckStatus.UNSUPPORTED))))
        ))
    }
}
