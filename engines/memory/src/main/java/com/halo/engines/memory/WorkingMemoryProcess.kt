package com.halo.engines.memory

import com.halo.runtime.kernel.Process
import com.halo.runtime.kernel.ProcessState
import com.halo.runtime.kernel.bus.PerceptionBus
import com.halo.runtime.kernel.bus.CognitiveBus
import com.halo.runtime.kernel.bus.ExecutionBus
import com.halo.icl.core.CognitiveObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WorkingMemoryProcess(
    private val perceptionBus: PerceptionBus,
    private val cognitiveBus: CognitiveBus,
    private val executionBus: ExecutionBus
) : Process {
    override val id: String = "engine.memory.working"
    override val name: String = "Working Memory Engine"
    
    private val _state = MutableStateFlow(ProcessState.Created)
    override val state: StateFlow<ProcessState> = _state
    
    // In-memory ephemeral state for Alpha
    private val _recentContext = MutableStateFlow<List<CognitiveObject>>(emptyList())
    val recentContext: StateFlow<List<CognitiveObject>> = _recentContext
    private val contextList = mutableListOf<CognitiveObject>()
    private val mutex = Mutex()
    private val maxItems = 100 // Prevent memory leaks in alpha
    
    override suspend fun start(): Unit = coroutineScope {
        _state.value = ProcessState.Running
        
        launch {
            perceptionBus.subscribe(id).collect { obj ->
                store(obj)
            }
        }
        
        launch {
            cognitiveBus.subscribe(id).collect { obj ->
                store(obj)
            }
        }
        
        launch {
            executionBus.subscribe(id).collect { obj ->
                store(obj)
            }
        }
    }
    
    private suspend fun store(obj: CognitiveObject) {
        mutex.withLock {
            contextList.add(obj)
            if (contextList.size > maxItems) {
                contextList.removeAt(0)
            }
            _recentContext.value = contextList.toList()
        }
    }
    
    suspend fun getRecentContext(): List<CognitiveObject> {
        return _recentContext.value
    }

    override suspend fun pause() { _state.value = ProcessState.Paused }
    override suspend fun resume() { _state.value = ProcessState.Running }
    override suspend fun stop() {
        _state.value = ProcessState.Stopped
        mutex.withLock {
            contextList.clear()
            _recentContext.value = emptyList()
        }
    }
}
