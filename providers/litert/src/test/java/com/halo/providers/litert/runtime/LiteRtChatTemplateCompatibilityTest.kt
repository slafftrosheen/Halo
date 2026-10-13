package com.halo.providers.litert.runtime

import com.halo.providers.litert.models.LiteRtModelCatalog
import org.junit.Assert.*
import org.junit.Test

class LiteRtChatTemplateCompatibilityTest {
    @Test fun `repair is restricted to the affected pinned artifact`() {
        assertNull(LiteRtChatTemplateCompatibility.overrideFor(null))
        assertNull(LiteRtChatTemplateCompatibility.overrideFor("unknown"))
        assertNull(LiteRtChatTemplateCompatibility.overrideFor(LiteRtModelCatalog.QWEN3_0_6B_DYNAMIC_INT4.sha256))
        val template = LiteRtChatTemplateCompatibility.overrideFor(LiteRtModelCatalog.QWEN3_1_7B_DYNAMIC_INT4.sha256)!!
        assertTrue(template.contains("macro halo_text"))
        assertTrue(template.contains("part.type == 'tool_response'"))
        assertTrue(template.contains("<tool_call>"))
        assertTrue(template.contains("enable_thinking"))
    }
}
