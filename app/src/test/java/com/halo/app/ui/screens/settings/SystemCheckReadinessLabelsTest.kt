package com.halo.app.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemCheckReadinessLabelsTest {
    @Test fun modelSelectionDoesNotFabricateProbeSuccess() {
        assertEquals(CheckStatus.READY, systemCheckConfiguredModelStatus(true))
        assertEquals(CheckStatus.NOT_CONFIGURED, systemCheckConfiguredModelStatus(false))
    }
    @Test fun statusIsNotAnApprovalToRunOtherActions() {
        assertEquals("Available", systemCheckStatusLabel(CheckStatus.READY))
        assertEquals("Passed", systemCheckStatusLabel(CheckStatus.PASSED))
    }
}
