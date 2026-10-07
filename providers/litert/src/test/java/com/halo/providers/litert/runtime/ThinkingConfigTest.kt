package com.halo.providers.litert.runtime

import com.google.ai.edge.litertlm.ConversationConfig
import com.halo.sdk.provider.RuntimeGenerationConfig
import com.halo.providers.litert.models.LiteRtModelCatalog
import org.junit.Assert.*
import org.junit.Test

class ThinkingConfigTest {
    @Test fun realSdkConversationReceivesDisabledThinkingAndZeroBudget() {
        val actual = ConversationConfig(thinkingConfig = thinkingConfigFor(RuntimeGenerationConfig("test", thinkingEnabled = false, thinkingTokenBudget = 64)))
        assertEquals(false, actual.thinkingConfig!!.enableThinking)
        assertEquals(0, actual.thinkingConfig!!.thinkingTokenBudget)
    }
    @Test fun enabledUsesExplicitBudgetAndDefaultRemainsNull() {
        val actual = ConversationConfig(thinkingConfig = thinkingConfigFor(RuntimeGenerationConfig("test", thinkingEnabled = true, thinkingTokenBudget = 64)))
        assertEquals(true, actual.thinkingConfig!!.enableThinking)
        assertEquals(64, actual.thinkingConfig!!.thinkingTokenBudget)
        assertEquals(-1, thinkingConfigFor(RuntimeGenerationConfig("test", thinkingEnabled = true))!!.thinkingTokenBudget)
        assertNull(thinkingConfigFor(RuntimeGenerationConfig("test")))
    }
    @Test fun exactQwenThinkingCapabilityPreservesOtherArtifactTruth() {
        assertTrue(LiteRtModelCatalog.byId("qwen3-0.6b-dynamic-int4")!!.supportsThinking)
        assertTrue(LiteRtModelCatalog.byId("qwen3-1.7b-dynamic-int4")!!.supportsThinking)
        assertFalse(LiteRtModelCatalog.SMOLLM2_135M_INSTRUCT.supportsThinking)
        assertFalse(LiteRtModelCatalog.SMOLLM2_360M_INSTRUCT.supportsThinking)
        assertFalse(LiteRtModelCatalog.QWEN2_5_1_5B_INSTRUCT_Q8.supportsThinking)
        assertTrue(LiteRtModelCatalog.DEEPSEEK_R1_DISTILL_QWEN_1_5B_Q8.supportsThinking)
    }
    @Test fun thinkingPolicyIsPartOfConfigurationIdentity() {
        val base = RuntimeGenerationConfig("test")
        assertNotEquals(base, base.copy(thinkingEnabled = false))
        assertNotEquals(base.hashCode(), base.copy(thinkingEnabled = false).hashCode())
        assertNotEquals(base.copy(thinkingEnabled = true, thinkingTokenBudget = 64), base.copy(thinkingEnabled = true, thinkingTokenBudget = 32))
    }
}
