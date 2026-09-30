package com.halo.runtime.cognition.memory

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Ephemeral owner-scoped notes. Never exported or mapped to MemoryRecord/Room. */
data class WorkingMemoryItem(val key: String,val content: String)
class InteractionWorkingMemory {
    private val mutex=Mutex()
    private val owners=LinkedHashMap<String,LinkedHashMap<String,String>>()
    suspend fun put(owner: String,key: String,content: String) = mutex.withLock {
        require(owner.length in 1..200 && key.length in 1..80 && content.length in 1..512)
        require(MemorySensitivityClassifier.classify(content) != MemorySensitivity.SECRET)
        if(owner !in owners && owners.size >= 32) owners.remove(owners.keys.first())
        val values=owners.getOrPut(owner) { LinkedHashMap() }
        if(key !in values && values.size >= 16) values.remove(values.keys.first())
        values[key]=content
    }
    suspend fun read(owner: String): List<WorkingMemoryItem> = mutex.withLock { owners[owner].orEmpty().map { WorkingMemoryItem(it.key,it.value) } }
    suspend fun discard(owner: String) { mutex.withLock { owners.remove(owner) } }
}
