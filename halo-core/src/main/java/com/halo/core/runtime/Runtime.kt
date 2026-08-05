package com.halo.core.runtime

import kotlinx.coroutines.flow.StateFlow

interface Runtime {
    suspend fun start()
    suspend fun stop()
    
    val state: StateFlow<RuntimeState>
}
