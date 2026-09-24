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
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.filter { it.matches(query) }
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

class SemanticMemoryStore : MemoryStore {
    private val memories = ConcurrentHashMap<UUID, Memory>()

    override suspend fun save(memory: Memory) { memories[memory.id] = memory }
    override suspend fun get(id: UUID): Memory? = memories[id]
    override suspend fun remove(id: UUID) { memories.remove(id) }
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.filter { it.matches(query) }
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

class EpisodicMemoryStore : MemoryStore {
    private val memories = ConcurrentHashMap<UUID, Memory>()

    override suspend fun save(memory: Memory) { memories[memory.id] = memory }
    override suspend fun get(id: UUID): Memory? = memories[id]
    override suspend fun remove(id: UUID) { memories.remove(id) }
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.filter { it.matches(query) }
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

class VectorStore : MemoryStore {
    private val memories = ConcurrentHashMap<UUID, Memory>()

    override suspend fun save(memory: Memory) { memories[memory.id] = memory }
    override suspend fun get(id: UUID): Memory? = memories[id]
    override suspend fun remove(id: UUID) { memories.remove(id) }
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.filter { it.matches(query) }
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

class KnowledgeStore : MemoryStore {
    private val memories = ConcurrentHashMap<UUID, Memory>()

    override suspend fun save(memory: Memory) { memories[memory.id] = memory }
    override suspend fun get(id: UUID): Memory? = memories[id]
    override suspend fun remove(id: UUID) { memories.remove(id) }
    override suspend fun search(query: MemoryQuery): List<Memory> = memories.values.filter { it.matches(query) }
    override suspend fun getAll(): List<Memory> = memories.values.toList()
}

private fun Memory.matches(query: MemoryQuery): Boolean {
    if (query.currentGoalId != null && this is com.halo.runtime.cognition.memory.EpisodeMemory &&
        goalId != query.currentGoalId) return false
    val terms = buildList {
        query.intent?.trim()?.takeIf(String::isNotEmpty)?.let(::add)
        addAll(query.entities.map(String::trim).filter(String::isNotEmpty))
        addAll(query.contextTags.map(String::trim).filter(String::isNotEmpty))
    }
    if (terms.isEmpty()) return true
    val searchable = when (this) {
        is com.halo.runtime.cognition.memory.FactMemory -> "$subject $predicate $obj"
        is com.halo.runtime.cognition.memory.EpisodeMemory -> "$executionResult $reflectionNotes"
        is com.halo.runtime.cognition.memory.SkillMemory -> "$action $capabilityId"
        is com.halo.runtime.cognition.memory.PreferenceMemory -> "$category $preference"
        is com.halo.runtime.cognition.memory.RelationshipMemory -> "$sourceNodeId $targetNodeId $relationType"
        is com.halo.runtime.cognition.memory.WorldStateMemory -> "$stateKey $stateValue"
    }
    return terms.all { searchable.contains(it, ignoreCase = true) }
}
