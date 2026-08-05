package com.halo.engines.memory

import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.memory.MemoryQuery
import com.halo.engines.memory.store.MemoryStore
import com.halo.engines.memory.service.RankingEngine

class RetrievalEngine(
    private val stores: List<MemoryStore>,
    private val rankingEngine: RankingEngine
) {
    suspend fun retrieve(query: MemoryQuery): List<Memory> {
        val candidateMemories = mutableListOf<Memory>()
        for (store in stores) {
            candidateMemories.addAll(store.search(query))
        }
        
        // Rank based on semantic similarity, importance, recency, etc.
        val ranked = rankingEngine.rank(candidateMemories, query)
        
        // Take top K
        return ranked.take(10)
    }
}

