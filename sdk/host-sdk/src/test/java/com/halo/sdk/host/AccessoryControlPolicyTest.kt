package com.halo.sdk.host

import org.junit.Assert.*
import org.junit.Test

class AccessoryControlPolicyTest {
    private fun button(control: AccessoryControl) = AccessoryControlEvent.Button(control, AccessoryControlAction.SINGLE, 1)
    @Test fun `idle Halo never consumes media and volume always remains system owned`() {
        assertEquals(AccessorySemanticAction.NONE, AccessoryControlPolicy.action(button(AccessoryControl.PLAY_PAUSE), false, true))
        assertEquals(AccessorySemanticAction.NONE, AccessoryControlPolicy.action(button(AccessoryControl.VOLUME_UP), true, true))
        assertEquals(AccessorySemanticAction.NONE, AccessoryControlPolicy.action(button(AccessoryControl.VOLUME_DOWN), true, true))
    }
    @Test fun `media controls require active speech and explicit policy`() {
        assertEquals(AccessorySemanticAction.NONE, AccessoryControlPolicy.action(button(AccessoryControl.PLAY_PAUSE), true, false))
        assertEquals(AccessorySemanticAction.STOP_SPEECH, AccessoryControlPolicy.action(button(AccessoryControl.PLAY_PAUSE), true, true))
        assertEquals(AccessorySemanticAction.INVOKE_ASSISTANT, AccessoryControlPolicy.action(button(AccessoryControl.ASSISTANT), false, false))
    }
}
