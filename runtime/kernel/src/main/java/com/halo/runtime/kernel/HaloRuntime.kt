package com.halo.runtime.kernel

import com.halo.sdk.runtime.Engine
import com.halo.sdk.runtime.RuntimeState
import com.halo.sdk.runtime.RuntimeContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HaloRuntime(
    private val engines: List<Engine>,
    private val runtimeContext: RuntimeContext
) : Runtime {

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Booting)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    override suspend fun boot() {
        // Simple initialization order
        engines.forEach {
            it.initialize(runtimeContext)
        }
        
        _state.value = RuntimeState.Ready
        
        engines.forEach {
            it.start()
        }
        
        _state.value = RuntimeState.Running
    }

    override suspend fun shutdown() {
        _state.value = RuntimeState.ShuttingDown
        
        // Reverse order shutdown
        engines.reversed().forEach {
            it.stop()
        }
        
        _state.value = RuntimeState.Offline
    }
}
