package com.halo.engines.memory

import com.halo.sdk.runtime.*

interface WorkingMemory {
    suspend fun store(fact: Fact)
    suspend fun retrieve(): List<Fact>
}

interface EpisodicMemory {
    suspend fun store(fact: Fact)
    suspend fun retrieve(): List<Fact>
}

interface SemanticMemory {
    suspend fun store(fact: Fact)
    suspend fun retrieve(): List<Fact>
}

interface VectorMemory {
    suspend fun store(fact: Fact)
    suspend fun retrieve(): List<Fact>
}
