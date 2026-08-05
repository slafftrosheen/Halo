package com.halo.runtime.kernel.service

import com.halo.runtime.kernel.CognitiveAgent
import com.halo.runtime.kernel.AgentPriority
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AgentScheduler {
    private val agentQueue = mutableListOf<Pair<CognitiveAgent, AgentPriority>>()
    private val mutex = Mutex()

    suspend fun schedule(agent: CognitiveAgent, priority: AgentPriority) {
        mutex.withLock {
            agentQueue.add(Pair(agent, priority))
            // Sort by priority descending
            agentQueue.sortByDescending { it.second.level }
        }
    }

    suspend fun getNext(): CognitiveAgent? {
        return mutex.withLock {
            if (agentQueue.isEmpty()) null else agentQueue.removeAt(0).first
        }
    }
}
