package com.halo.runtime.kernel

import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.attention.AttentionContext
import com.halo.runtime.cognition.DeviceState

data class AgentContext(
    val relevantMemories: List<Memory>,
    val relevantGoals: List<Goal>,
    val capabilities: Set<Capability>,
    val permissions: Set<String>,
    val attention: AttentionContext,
    val budget: CognitiveBudget,
    val worldState: DeviceState
)

