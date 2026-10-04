package com.halo.cognition.language

import org.junit.Assert.*
import org.junit.Test

class PoliteCommandTest {
    @Test fun commonSpokenRequestsUseNativeCommands() {
        assertEquals(TimerIntent(300), NativeCommandParser.parse("Could you please set a timer for five minutes?"))
        assertEquals(VolumeIntent(percent = 30), NativeCommandParser.parse("Can you set volume to 30 percent?"))
        assertEquals(NativeOpenApplicationIntent("Spotify"), NativeCommandParser.parse("Would you please open Spotify?"))
        assertEquals(FlashlightIntent(true), NativeCommandParser.parse("Please turn on the flashlight."))
    }
    @Test fun flexiblePhoneRequestsRequireTools() {
        assertEquals(ToolActionReason.ACTION_INTENT, ConversationSemantics.actionReason("Can you change my phone volume to half?"))
        assertEquals(ToolActionReason.SCREEN_OPERATION, ConversationSemantics.actionReason("Could you tap the first item?"))
    }
    @Test fun explanationsAndOptOutRemainConversation() {
        assertTrue(ConversationSemantics.isExplanation(ConversationSemantics.commandText("Can you explain how to change volume?")))
        assertNull(NativeCommandParser.parse("Could you explain how to open Spotify?"))
        assertNull(ConversationSemantics.actionReason("Do not use tools, explain phone settings"))
    }
}
