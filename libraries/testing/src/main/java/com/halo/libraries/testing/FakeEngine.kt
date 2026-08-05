package com.halo.libraries.testing

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.reflect.KClass

open class FakeEngine : Engine {
    override val metadata = EngineMetadata(
        id = Identifier(this::class.java.simpleName),
        version = Version(1, 0, 0),
        description = "Fake Engine for testing"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health.asStateFlow()

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state.asStateFlow()

    override val dependencies: Set<ServiceKey<*>> = emptySet()

    protected lateinit var runtimeContext: EngineContext

    override suspend fun initialize(context: EngineContext) {
        this.runtimeContext = context
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        _state.value = EngineState.Running
    }

    override suspend fun stop() {
        _state.value = EngineState.Stopped
    }
}
