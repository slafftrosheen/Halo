package com.halo.runtime.kernel.service

import com.halo.runtime.kernel.CognitiveAgent

class AgentRegistry {
    private val agents = mutableMapOf<String, CognitiveAgent>()

    fun register(agent: CognitiveAgent) {
        agents[agent.id] = agent
    }

    fun unregister(agentId: String) {
        agents.remove(agentId)
    }

    fun getAgent(agentId: String): CognitiveAgent? {
        return agents[agentId]
    }

    fun getAllAgents(): List<CognitiveAgent> {
        return agents.values.toList()
    }
}
