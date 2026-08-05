package com.halo.runtime.intents

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.runtime.*
import com.halo.runtime.plugins.PluginManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class IntentEngine : Engine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.runtime.intents"),
        version = Version(1, 0, 0),
        description = "Intent Parsing and Dispatch"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = setOf(com.halo.runtime.services.Services.EventBus, com.halo.runtime.services.Services.PluginManager)

    private lateinit var context: EngineContext
    private val parser: IntentParser = DefaultIntentParser()
    private lateinit var pluginManager: PluginManager
    
    private val scope = CoroutineScope(Dispatchers.Default)

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
        pluginManager = context.registry.resolve(com.halo.runtime.services.Services.PluginManager)
    }

    override suspend fun start() {
        _state.value = EngineState.Running
        scope.launch {
            context.runtime.eventBus.observe(SpeechRecognized::class.java).collect {
                val intent = parser.parse(it.text)
                context.runtime.eventBus.publish(IntentRecognized(System.currentTimeMillis(), intent))
                pluginManager.dispatch(intent)
            }
        }
    }

    override suspend fun stop() {
        _state.value = EngineState.Stopped
    }
}
