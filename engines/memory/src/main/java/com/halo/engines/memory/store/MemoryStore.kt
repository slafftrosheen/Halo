package com.halo.engines.memory.store

import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.memory.MemoryQuery
import java.util.UUID

interface MemoryStore {
    suspend fun save(memory: Memory)
    suspend fun get(id: UUID): Memory?
    suspend fun remove(id: UUID)
    suspend fun search(query: MemoryQuery): List<Memory>
    suspend fun getAll(): List<Memory>
}

