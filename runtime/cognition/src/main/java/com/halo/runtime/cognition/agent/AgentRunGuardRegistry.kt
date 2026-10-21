package com.halo.runtime.cognition.agent

import com.halo.cognition.language.InteractionId
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiWorkloadKind
import com.halo.sdk.provider.AiWorkloadSpec
import com.halo.sdk.provider.ToolUseMode
import java.util.concurrent.ConcurrentHashMap

/**
 * MINING-P1-001: one guard per run, shared by every executor that participates in that run.
 *
 * A run's bounded-ness is only meaningful if the provider loop and the tool loop consult the *same*
 * counters and the *same* thermal reading. The registry is the single owner of that state; it does
 * not replace [AgentRunStore], which remains the durable execution truth.
 */
class AgentRunGuardRegistry(
    private val limits: (AgentRunKind) -> AgentRunLimits = AgentRunLimits::forKind,
    private val thermalGate: AgentRunThermalGate = AgentRunThermalGate.NONE,
    private val clock: AgentRunClock = AgentRunClock.System,
) {

    private val guards = ConcurrentHashMap<String, AgentRunGuard>()

    fun limitsFor(kind: AgentRunKind): AgentRunLimits = limits(kind)

    fun guardFor(
        runId: AgentRunId,
        interactionId: InteractionId? = null,
        kind: AgentRunKind = AgentRunKind.INTERACTIVE,
        workload: AiWorkloadSpec? = null,
        local: Boolean = false,
        hasImage: Boolean = false,
    ): AgentRunGuard = guards.getOrPut(runId.value) {
        val base = limits(kind)
        // Only the default interactive policy gains an image-specific deadline.
        // Preserve custom ceilings, remote turns, and every tool/action lane.
        val selected = if (kind == AgentRunKind.INTERACTIVE && base == AgentRunLimits.INTERACTIVE &&
            local && hasImage && workload?.kind == AiWorkloadKind.VISUAL_UNDERSTANDING &&
            AiCapability.IMAGE in workload.requiredCapabilities && workload.toolUseMode == ToolUseMode.NONE)
            AgentRunLimits.LOCAL_IMAGE else base
        AgentRunGuard(
            runId = runId.value,
            interactionId = interactionId?.value,
            limits = selected,
            thermalGate = thermalGate,
            clock = clock,
        )
    }

    fun peek(runId: String): AgentRunGuard? = guards[runId]
    fun registerChild(runId: AgentRunId, interactionId: InteractionId, local: Boolean): AgentRunGuard = guards.getOrPut(runId.value) {
        AgentRunGuard(runId.value, interactionId.value, AgentRunLimits.DELEGATED,
            if (local) thermalGate else AgentRunThermalGate.NONE, clock)
    }

    /** Called when a run reaches a terminal state so the guard cannot outlive its run. */
    fun release(runId: String) {
        guards.remove(runId)
    }

    fun activeRunCount(): Int = guards.size
}
