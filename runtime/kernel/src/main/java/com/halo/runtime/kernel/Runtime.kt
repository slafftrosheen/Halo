package com.halo.runtime.kernel

import com.halo.sdk.runtime.RuntimeState
import kotlinx.coroutines.flow.StateFlow

interface Runtime {
    val state: StateFlow<RuntimeState>
    suspend fun boot()
    suspend fun shutdown()
}
