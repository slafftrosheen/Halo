package com.halo.runtime.kernel

import com.halo.core.engine.ContextEngine
import com.halo.core.engine.MemoryEngine
import com.halo.core.engine.VoiceEngine
import com.halo.core.engine.WorkflowEngine
import com.halo.core.runtime.Runtime
import com.halo.core.runtime.RuntimeState
import com.halo.core.runtime.RuntimeContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HaloRuntime(
    private val voice: VoiceEngine,
    private val memory: MemoryEngine,
    private val workflow: WorkflowEngine,
    private val contextEngine: ContextEngine,
    private val runtimeContext: RuntimeContext
) : Runtime {

    private val _state = MutableStateFlow(RuntimeState.BOOT)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()
    
    suspend fun boot() {
        _state.value = RuntimeState.INITIALIZING
        
        voice.initialize(runtimeContext)
        memory.initialize(runtimeContext)
        workflow.initialize(runtimeContext)
        contextEngine.initialize(runtimeContext)
    }

    override suspend fun start() {
        if (_state.value == RuntimeState.BOOT) {
            boot()
        }
        
        voice.start()
        memory.start()
        workflow.start()
        contextEngine.start()
        
        _state.value = RuntimeState.READY
    }

    override suspend fun stop() {
        _state.value = RuntimeState.SHUTDOWN
        
        voice.stop()
        memory.stop()
        workflow.stop()
        contextEngine.stop()
        
        _state.value = RuntimeState.SLEEPING
    }
}
