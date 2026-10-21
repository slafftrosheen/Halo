package com.halo.runtime.cognition.agent

import com.halo.sdk.provider.*
import org.junit.Assert.*
import org.junit.Test

class LocalImageRunDeadlineTest {
    private class Clock(var now: Long = 0) : AgentRunClock { override fun nowMs() = now }
    private val vision = AiRoleRequirements.workload(AiModelRole.VISION).copy(kind = AiWorkloadKind.VISUAL_UNDERSTANDING)

    @Test fun localImageOnlyTurnHasAFiniteLongerDeadlineAndUnchangedOtherLimits() {
        val clock = Clock()
        val registry = AgentRunGuardRegistry(clock = clock)
        val id = AgentRunId("image")
        val guard = registry.guardFor(id, workload = vision, local = true, hasImage = true)
        assertEquals(240_000L, guard.remainingMs)
        assertEquals(AgentRunLimits.INTERACTIVE, AgentRunLimits.LOCAL_IMAGE.copy(maxWallClockMs = 90_000))
        clock.now = 100_000
        assertEquals(RunGuardDecision.Continue, guard.beforeRound())
        assertSame(guard, registry.guardFor(id))
        clock.now = 240_001
        assertEquals(RunGuardReason.DEADLINE_EXCEEDED, (guard.beforeRound() as RunGuardDecision.Stop).reason)
    }

    @Test fun remoteTextAndToolTurnsKeepTheInteractiveCeiling() {
        for ((workload, local, image) in listOf(
            Triple(vision, false, true), Triple(vision, true, false),
            Triple(AiWorkloadSpec(), true, true),
            Triple(vision.copy(requiredCapabilities = setOf(AiCapability.TEXT)), true, true),
            Triple(vision.copy(tools = AiFeatureRequirement.OPTIONAL), true, true),
            Triple(vision.copy(tools = AiFeatureRequirement.REQUIRED), true, true))) {
            assertEquals(90_000L, AgentRunGuardRegistry(clock = Clock()).guardFor(AgentRunId("ordinary"),
                workload = workload, local = local, hasImage = image).remainingMs)
        }
        for (kind in listOf(AgentRunKind.NATIVE, AgentRunKind.SYSTEM, AgentRunKind.ROUTINE)) {
            assertEquals(AgentRunLimits.forKind(kind).maxWallClockMs,
                AgentRunGuardRegistry(clock = Clock()).guardFor(AgentRunId("other-kind"), kind = kind,
                    workload = vision, local = true, hasImage = true).remainingMs)
        }
    }

    @Test fun customCeilingsAndExistingRunsCannotBeExtended() {
        val clock = Clock()
        val custom = AgentRunGuardRegistry(limits = { AgentRunLimits.INTERACTIVE.copy(maxWallClockMs = 5_000) }, clock = clock)
        assertEquals(5_000L, custom.guardFor(AgentRunId("custom"), workload = vision, local = true, hasImage = true).remainingMs)
        val registry = AgentRunGuardRegistry(clock = clock)
        val id = AgentRunId("existing")
        registry.guardFor(id)
        assertEquals(90_000L, registry.guardFor(id, workload = vision, local = true, hasImage = true).remainingMs)
    }
}
