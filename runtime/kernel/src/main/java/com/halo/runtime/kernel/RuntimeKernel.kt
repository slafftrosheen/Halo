package com.halo.runtime.kernel

import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RuntimeKernel(
    engines: List<Engine>,
    private val runtimeContext: RuntimeContext
) : Runtime {

    private val graph = EngineGraph()
    private val resolver = DependencyResolver()
    private val lifecycle = LifecycleManager()
    private val bootLoader = BootLoader(graph, resolver, lifecycle)

    private val _state = MutableStateFlow<RuntimeState>(RuntimeState.Offline)
    override val state: StateFlow<RuntimeState> = _state.asStateFlow()

    init {
        engines.forEach { graph.register(it) }
    }

    override suspend fun boot() {
        bootLoader.boot(runtimeContext) { newState ->
            _state.value = newState
        }
    }

    override suspend fun shutdown() {
        bootLoader.shutdown(runtimeContext) { newState ->
            _state.value = newState
        }
    }
}
