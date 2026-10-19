package com.halo.app.ai

import com.halo.engines.interpretation.rules.ModelIntentDecision
import org.junit.Assert.*
import org.junit.Test

class LocalIntentModelRouterTest {
    @Test fun sharedLiteRtResidentChatIsNotEvictedForIntent() {
        val candidate = LocalIntentModelRouter.DEFAULT_ID
        assertFalse(LocalIntentModelRouter.shouldDefer(null,candidate,false))
        assertFalse(LocalIntentModelRouter.shouldDefer(candidate,candidate,false))
        assertTrue(LocalIntentModelRouter.shouldDefer("lfm2.5-1.2b",candidate,false))
        assertTrue(LocalIntentModelRouter.shouldDefer(candidate,candidate,true))
    }

    @Test fun onlyStandaloneIntentLabelsAreAccepted() {
        assertEquals(ModelIntentDecision.ACTION, LocalIntentModelRouter.parse(" ACTION\n"))
        assertEquals(ModelIntentDecision.ANSWER, LocalIntentModelRouter.parse("answer"))
        assertEquals(ModelIntentDecision.UNCERTAIN, LocalIntentModelRouter.parse("UNCERTAIN"))
        assertNull(LocalIntentModelRouter.parse("ACTION because open"))
        assertNull(LocalIntentModelRouter.parse("{\"intent\":\"ACTION\"}"))
        assertNull(LocalIntentModelRouter.parse("<halo_tool_call>{\"name\":\"halo_open_app\"}</halo_tool_call>"))
        assertNull(LocalIntentModelRouter.parse("ACTION\nANSWER"))
        assertNull(LocalIntentModelRouter.parse(""))
    }
}
