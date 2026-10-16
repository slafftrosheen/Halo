package com.halo.app.ui.screens.settings

import com.halo.app.ui.icons.HaloGlyph
import org.junit.Assert.*
import org.junit.Test

class SystemCheckOverviewTest {
    private fun item(id: String, status: CheckStatus) =
        SystemCheckItem(id, id, HaloGlyph.DIAGNOSTICS, status, "Actual observation")

    @Test fun overviewUsesOnlyFiveRealSourceItems() {
        val sections = listOf(SystemCheckSection("runtime", "Runtime", listOf(
            item("core", CheckStatus.READY),
            item("ai_selection", CheckStatus.NOT_CONFIGURED),
            item("phantom", CheckStatus.PASSED),
        )))
        assertEquals(listOf("core", "ai_selection"),
            systemCheckEssentialItems(sections).map { it.id })
        assertEquals("Available", systemCheckStatusLabel(CheckStatus.READY))
        assertEquals("Passed", systemCheckStatusLabel(CheckStatus.PASSED))
    }
    @Test fun setupShortcutIsConditionalAndNeverRunsAProbe() {
        val sections = listOf(SystemCheckSection("s", "S", listOf(
            item("mic_permission", CheckStatus.NOT_CONFIGURED),
            item("ai_selection", CheckStatus.NOT_CONFIGURED),
        )))
        assertEquals(SystemCheckSetupTarget.INTELLIGENCE,
            systemCheckSafeSetupTarget(sections))
        assertEquals(SystemCheckSetupTarget.VOICE,
            systemCheckSafeSetupTarget(listOf(SystemCheckSection("s", "S",
                listOf(item("mic_permission", CheckStatus.NOT_CONFIGURED))))))
        assertNull(systemCheckSafeSetupTarget(listOf(SystemCheckSection("s", "S",
            listOf(item("ai_selection", CheckStatus.PASSED))))))
    }
    @Test fun passiveRowsCannotAccidentallyLaunchDiagnostics() {
        assertNull(systemCheckExplicitActionLabel(item("core", CheckStatus.READY)))
        assertNull(systemCheckExplicitActionLabel(item("mic_permission", CheckStatus.NOT_CONFIGURED)))
        assertNull(systemCheckExplicitActionLabel(item("mic_capture", CheckStatus.RUNNING)))
        assertEquals("Test microphone", systemCheckExplicitActionLabel(
            item("mic_capture", CheckStatus.READY)))
        assertEquals("Unload local model", systemCheckExplicitActionLabel(
            item("litert_control", CheckStatus.BLOCKED)))
    }
}
