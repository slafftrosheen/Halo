package com.halo.runtime.intents

import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.sdk.runtime.*
import com.halo.runtime.plugins.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class IntentEngine(
    private val eventBus: EventBus,
    private val pluginManager: PluginManager
) : Engine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.runtime.intents"),
        version = Version(1, 0, 0),
        description = "Intent Parsing and Dispatch"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = emptySet()

    private lateinit var context: EngineContext
    private val parser: IntentParser = DefaultIntentParser()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var recognitionJob: Job? = null

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        if (recognitionJob?.isActive == true) return

        _state.value = EngineState.Running
        recognitionJob = scope.launch {
            eventBus.observe(SpeechRecognized::class.java).collect {
                val intent = parser.parse(it.text)
                eventBus.publish(
                    IntentRecognized(
                        timestamp = System.currentTimeMillis(),
                        intent = intent
                    )
                )
            }
        }
    }

    override suspend fun stop() {
        recognitionJob?.cancel()
        recognitionJob = null
        _state.value = EngineState.Stopped
    }
}
