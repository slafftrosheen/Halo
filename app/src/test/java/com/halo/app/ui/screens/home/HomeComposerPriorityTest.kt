package com.halo.app.ui.screens.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeComposerPriorityTest {
    @Test fun usableDraftChoosesSendAndEmptyComposerChoosesVoice() {
        assertEquals(HomeComposerAction.SEND, homeComposerPrimaryAction("Question", true, true, false, true))
        assertEquals(HomeComposerAction.VOICE, homeComposerPrimaryAction(" ", true, true, false, true))
    }
    @Test fun stopWinsAndSetupOrReviewRetainsPriorityOutsideComposer() {
        assertEquals(HomeComposerAction.STOP, homeComposerPrimaryAction("Question", false, true, true, true))
        assertEquals(HomeComposerAction.NONE, homeComposerPrimaryAction("Question", true, true, false, false))
        assertEquals(HomeComposerAction.NONE, homeComposerPrimaryAction("Question", false, true, false, true))
        assertEquals(HomeComposerAction.NONE, homeComposerPrimaryAction("", true, false, false, true))
    }
}
