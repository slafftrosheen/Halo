package com.halo.runtime.engine.fake

import com.halo.core.engine.*
import com.halo.core.runtime.RuntimeContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

abstract class BaseFakeEngine : Engine {
    override val id: String = this::class.java.simpleName
    protected val _health = MutableStateFlow(EngineHealth.HEALTHY)
    override val health: StateFlow<EngineHealth> = _health.asStateFlow()

    protected val _state = MutableStateFlow(EngineState.CREATED)
    override val state: StateFlow<EngineState> = _state.asStateFlow()
    
    protected lateinit var runtimeContext: RuntimeContext

    override suspend fun initialize(context: RuntimeContext) {
        this.runtimeContext = context
        _state.value = EngineState.INITIALIZED
    }

    override suspend fun start() {
        _state.value = EngineState.READY
    }

    override suspend fun stop() {
        _state.value = EngineState.STOPPED
    }
    
    override suspend fun shutdown() {
        _state.value = EngineState.CREATED
    }
}

class FakeVoiceEngine : BaseFakeEngine(), VoiceEngine {
    override suspend fun listen() {
        _state.value = EngineState.BUSY
    }

    override suspend fun stopListening() {
        _state.value = EngineState.READY
    }
}

class FakeMemoryEngine : BaseFakeEngine(), MemoryEngine {
    override suspend fun remember(data: Any) {}
    override suspend fun search(query: String): List<Any> = emptyList()
}

class FakeWorkflowEngine : BaseFakeEngine(), WorkflowEngine {
    override suspend fun execute(intent: String) {}
}

class FakeContextEngine : BaseFakeEngine(), ContextEngine {
    private val _context = MutableStateFlow(object : ContextSnapshot {})
    override val context: StateFlow<ContextSnapshot> = _context.asStateFlow()
}
