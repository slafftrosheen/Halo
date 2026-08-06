package com.halo.engines.memory.store

import com.halo.runtime.cognition.memory.Memory
import com.halo.runtime.cognition.memory.MemoryQuery
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class WorkingMemoryStore : MemoryStore {
    private val memories = ConcurrentHashMap<UUID, Memory>()

    override suspend fun save(memory: Memory) { memories[memory.id] = memory }
    override suspend fun get(id: UUID): Memory? = memories[id]
    override suspend fun remove(id: UUID) { memories.remove(id) }
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.toList() // Filter logic in RankingEngine
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

class SemanticMemoryStore : MemoryStore {
    private val memories = ConcurrentHashMap<UUID, Memory>()

    override suspend fun save(memory: Memory) { memories[memory.id] = memory }
    override suspend fun get(id: UUID): Memory? = memories[id]
    override suspend fun remove(id: UUID) { memories.remove(id) }
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.toList()
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

class EpisodicMemoryStore : MemoryStore {
    private val memories = ConcurrentHashMap<UUID, Memory>()

    override suspend fun save(memory: Memory) { memories[memory.id] = memory }
    override suspend fun get(id: UUID): Memory? = memories[id]
    override suspend fun remove(id: UUID) { memories.remove(id) }
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.toList()
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

class VectorStore : MemoryStore {
    private val memories = ConcurrentHashMap<UUID, Memory>()

    override suspend fun save(memory: Memory) { memories[memory.id] = memory }
    override suspend fun get(id: UUID): Memory? = memories[id]
    override suspend fun remove(id: UUID) { memories.remove(id) }
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.toList() // Mocked semantic search
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

class KnowledgeStore : MemoryStore {
    private val memories = ConcurrentHashMap<UUID, Memory>()

    override suspend fun save(memory: Memory) { memories[memory.id] = memory }
    override suspend fun get(id: UUID): Memory? = memories[id]
    override suspend fun remove(id: UUID) { memories.remove(id) }
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.toList() // Mocked graph traversal
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

