package com.halo.app.ai

import com.halo.engines.interpretation.rules.ModelIntentDecision
import org.junit.Assert.*
import org.junit.Test

class LocalIntentModelRouterTest {
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
