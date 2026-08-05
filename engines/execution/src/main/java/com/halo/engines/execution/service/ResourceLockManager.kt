package com.halo.engines.execution.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ResourceLockManager {
    private val locks = mutableMapOf<String, Mutex>()
    private val globalLock = Mutex()

    suspend fun <T> withLock(resourceId: String, block: suspend () -> T): T {
        val mutex = globalLock.withLock {
            locks.getOrPut(resourceId) { Mutex() }
        }
        return mutex.withLock { block() }
    }
}
