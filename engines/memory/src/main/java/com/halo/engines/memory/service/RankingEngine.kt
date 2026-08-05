package com.halo.engines.memory.service

import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.memory.MemoryQuery

class RankingEngine {
    fun rank(memories: List<Memory>, query: MemoryQuery): List<Memory> {
        return memories.sortedByDescending { memory ->
            var score = 0.0f
            
            // Base importance and confidence
            score += memory.importance * 2.0f
            score += memory.confidence
            
            // Frequency boost
            score += (memory.frequency * 0.1f)
            
            // Recency could also affect score positively or negatively based on context
            
            score
        }
    }
}

