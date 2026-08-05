package com.halo.core.engine

interface MemoryEngine : Engine {
    suspend fun remember(data: Any)
    suspend fun search(query: String): List<Any>
}
