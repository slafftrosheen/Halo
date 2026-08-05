package com.halo.engines.memory
import com.halo.sdk.runtime.Engine
interface MemoryEngine : Engine {
    suspend fun remember()
    suspend fun search()
}
