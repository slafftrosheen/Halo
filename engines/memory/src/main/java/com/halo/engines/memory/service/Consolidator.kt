package com.halo.engines.memory.service

import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.memory.FactMemory
import com.halo.engines.memory.store.WorkingMemoryStore
import com.halo.engines.memory.store.SemanticMemoryStore
import com.halo.engines.memory.store.EpisodicMemoryStore

class Consolidator(
    private val workingStore: WorkingMemoryStore,
    private val semanticStore: SemanticMemoryStore,
    private val episodicStore: EpisodicMemoryStore
) {
    suspend fun consolidate() {
        val workingMemories = workingStore.getAll()
        
        for (memory in workingMemories) {
            if (memory.importance > 0.8f || memory.frequency > 5) {
                // Example logic to consolidate highly repeated/important items
                if (memory is FactMemory) {
                    semanticStore.save(memory)
                } else {
                    episodicStore.save(memory)
                }
                workingStore.remove(memory.id)
            }
        }
    }
}

