package com.halo.runtime.kernel

import com.halo.runtime.cognition.Capability
import java.util.UUID

interface CognitiveAgent {
    val id: String
    val capabilities: Set<Capability>

    suspend fun perceive(context: AgentContext)
    suspend fun think(context: AgentContext)
    suspend fun plan(context: AgentContext)
    suspend fun execute(context: AgentContext)
}
