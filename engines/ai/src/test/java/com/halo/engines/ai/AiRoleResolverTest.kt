package com.halo.engines.ai

import com.halo.sdk.provider.*
import org.junit.Assert.*
import org.junit.Test

class AiRoleResolverTest {
    private val resolver = DefaultAiRoleResolver()
    @Test fun imageOverridesToolsAndPreferredChat() {
        assertEquals(AiModelRole.VISION, resolver.determineRole(AiWorkloadSpec(
            requiredCapabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE),
            tools = AiFeatureRequirement.REQUIRED, preferredRole = AiModelRole.CHAT)))
    }
    @Test fun compoundToolsOverridePreferredChat() {
        assertEquals(AiModelRole.AGENTIC, resolver.determineRole(AiWorkloadSpec(
            tools = AiFeatureRequirement.REQUIRED, preferredRole = AiModelRole.CHAT)))
        assertEquals(AiModelRole.CHAT, resolver.determineRole(AiWorkloadSpec()))
    }
}
