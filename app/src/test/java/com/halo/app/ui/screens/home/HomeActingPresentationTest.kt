package com.halo.app.ui.screens.home

import com.halo.sdk.provider.CognitiveSessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeActingPresentationTest {
    @Test fun onlyActivePhoneActionsWithoutSuppliedFxUseNeutralWorkingCard() {
        assertTrue(homeShouldShowUnmappedPhoneAction("halo_ui_tap"))
        assertTrue(homeShouldShowUnmappedPhoneAction("halo_perform_app_action"))
        assertFalse(homeShouldShowUnmappedPhoneAction("halo_open_app"))
        assertFalse(homeShouldShowUnmappedPhoneAction("halo_timer"))
        assertFalse(homeShouldShowUnmappedPhoneAction(null))
    }

    @Test fun actionPlanIsPromotedOnlyWhenRuntimeIsExecutingATool() {
        assertTrue(homeShouldPromoteActivePlan(
            CognitiveSessionState.ExecutingTool("halo_ui_tap"), true))
        assertFalse(homeShouldPromoteActivePlan(
            CognitiveSessionState.ExecutingTool("halo_ui_tap"), false))
        assertFalse(homeShouldPromoteActivePlan(
            CognitiveSessionState.Reasoning("litert"), true))
        assertFalse(homeShouldPromoteActivePlan(CognitiveSessionState.Idle, true))
    }
}
