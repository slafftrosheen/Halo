package com.halo.runtime.cognition.context

import com.halo.runtime.cognition.tools.ConfirmationPolicy
import com.halo.runtime.cognition.tools.DefaultToolExposureGate
import com.halo.runtime.cognition.tools.ToolCategory
import com.halo.runtime.cognition.tools.ToolDescriptor
import com.halo.runtime.cognition.tools.ToolExposureContext
import com.halo.runtime.cognition.tools.ToolExposureDecision
import com.halo.runtime.cognition.tools.ToolExposureDenyReason
import com.halo.runtime.cognition.tools.ToolId
import com.halo.runtime.cognition.tools.ToolRiskClass
import com.halo.runtime.cognition.tools.ToolSecurityPolicy
import com.halo.sdk.provider.AiContextPolicy
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.runtime.skills.SkillId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolExposureGateTest {

    private fun createDescriptor(
        id: String,
        category: ToolCategory = ToolCategory.SYSTEM,
        risk: ToolRiskClass = ToolRiskClass.READ_ONLY
    ): ToolDescriptor {
        val definition = ToolDefinition(id = id, name = id, description = "Test tool $id")
        return ToolDescriptor(
            id = ToolId(id),
            definition = definition,
            category = category,
            security = ToolSecurityPolicy(risk = risk, confirmation = ConfirmationPolicy.NONE)
        )
    }

    @Test
    fun `read-only tool allowed in ASK and PLAN mode`() {
        val gate = DefaultToolExposureGate()
        val readOnlyTool = createDescriptor("halo_search_memory", risk = ToolRiskClass.READ_ONLY)

        val askContext = ToolExposureContext(mode = CognitiveOperatingMode.ASK)
        val planContext = ToolExposureContext(mode = CognitiveOperatingMode.PLAN)

        assertEquals(ToolExposureDecision.Allow, gate.evaluate(readOnlyTool, askContext))
        assertEquals(ToolExposureDecision.Allow, gate.evaluate(readOnlyTool, planContext))
    }

    @Test
    fun `side-effect tool denied in ASK and PLAN mode`() {
        val gate = DefaultToolExposureGate()
        val sideEffectTool = createDescriptor("halo_create_note", risk = ToolRiskClass.REVERSIBLE)

        val askContext = ToolExposureContext(mode = CognitiveOperatingMode.ASK)
        val planContext = ToolExposureContext(mode = CognitiveOperatingMode.PLAN)

        val decisionAsk = gate.evaluate(sideEffectTool, askContext)
        val decisionPlan = gate.evaluate(sideEffectTool, planContext)

        assertTrue(decisionAsk is ToolExposureDecision.Deny)
        assertEquals(ToolExposureDenyReason.MODE_DISALLOWS_SIDE_EFFECT, (decisionAsk as ToolExposureDecision.Deny).reason)

        assertTrue(decisionPlan is ToolExposureDecision.Deny)
        assertEquals(ToolExposureDenyReason.MODE_DISALLOWS_SIDE_EFFECT, (decisionPlan as ToolExposureDecision.Deny).reason)
    }

    @Test
    fun `side-effect tool allowed in AGENT and AUTO mode`() {
        val gate = DefaultToolExposureGate()
        val sideEffectTool = createDescriptor("halo_create_note", risk = ToolRiskClass.REVERSIBLE)

        val autoContext = ToolExposureContext(mode = CognitiveOperatingMode.AUTO)
        val agentContext = ToolExposureContext(mode = CognitiveOperatingMode.AGENT)

        assertEquals(ToolExposureDecision.Allow, gate.evaluate(sideEffectTool, autoContext))
        assertEquals(ToolExposureDecision.Allow, gate.evaluate(sideEffectTool, agentContext))
    }

    @Test
    fun `cloud memory search denied when allowCloudMemories is false`() {
        val gate = DefaultToolExposureGate()
        val memoryTool = createDescriptor("halo_search_memory", category = ToolCategory.WORKSPACE, risk = ToolRiskClass.READ_ONLY)

        val remoteContext = ToolExposureContext(
            isLocalTarget = false,
            policy = AiContextPolicy(allowCloudMemories = false)
        )

        val decision = gate.evaluate(memoryTool, remoteContext)
        assertTrue(decision is ToolExposureDecision.Deny)
        assertEquals(ToolExposureDenyReason.REMOTE_MEMORY_DISABLED, (decision as ToolExposureDecision.Deny).reason)
    }
}
