package com.halo.runtime.kernel

import com.halo.runtime.cognition.Capability
import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.AttentionState
import com.halo.runtime.cognition.DeviceState

data class AgentContext(
    val relevantMemories: List<Memory>,
    val relevantGoals: List<Goal>,
    val capabilities: Set<Capability>,
    val permissions: Set<String>,
    val attention: AttentionState,
    val budget: CognitiveBudget,
    val worldState: DeviceState
)

