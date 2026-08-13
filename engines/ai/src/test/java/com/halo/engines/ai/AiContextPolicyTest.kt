package com.halo.engines.ai

import org.junit.Assert.*
import org.junit.Test

class AiContextPolicyTest {

    @Test
    fun `conservative policy defaults are correct`() {
        val policy = AiContextPolicy.CONSERVATIVE
        assertTrue(policy.includeConversation)
        assertTrue(policy.includeDeviceState)
        assertFalse(policy.includeRecentEvents)
        assertFalse(policy.includeMemories)
        assertTrue(policy.includeAvailableTools)
    }

    @Test
    fun `policy can be customized`() {
        val policy = AiContextPolicy(includeMemories = true, includeImages = false)
        assertTrue(policy.includeMemories)
        assertFalse(policy.includeImages)
        // Others should remain default
        assertTrue(policy.includeConversation)
    }
}
