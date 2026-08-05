package com.halo.runtime.kernel.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ResourceManager {
    private val resources = mutableMapOf<String, Mutex>()
    private val globalLock = Mutex()

    suspend fun <T> requestResource(resourceId: String, agentId: String, block: suspend () -> T): T {
        val mutex = globalLock.withLock {
            resources.getOrPut(resourceId) { Mutex() }
        }
        return mutex.withLock {
            // Log resource acquisition by agentId
            block()
        }
    }
}
