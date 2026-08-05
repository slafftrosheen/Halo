package com.halo.app.data

import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {
    val allMemories: Flow<List<Memory>> = memoryDao.getAllMemories()

    suspend fun insert(memory: Memory) = memoryDao.insertMemory(memory)
    
    suspend fun update(memory: Memory) = memoryDao.updateMemory(memory)

    suspend fun delete(memory: Memory) = memoryDao.deleteMemory(memory)
}
