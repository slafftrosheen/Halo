package com.halo.engines.kernel

import com.halo.sdk.runtime.*
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.services.Services
import com.halo.runtime.kernel.service.*
import com.halo.runtime.kernel.AgentContext
import com.halo.runtime.cognition.AttentionState
import com.halo.runtime.cognition.DeviceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AgentKernelImpl(
    private val scheduler: AgentScheduler,
    private val resourceManager: ResourceManager,
    private val permissionManager: PermissionManager,
    private val consensusEngine: ConsensusEngine,
    private val budgetManager: BudgetManager,
    private val agentRegistry: AgentRegistry,
    private val messageBus: MessageBus
) : Engine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.kernel.core"),
        version = Version(1, 0, 0),
        description = "Core Agent Kernel"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = setOf(Services.EventBus)

    private lateinit var context: EngineContext
    private val scope = CoroutineScope(Dispatchers.Default)
    private var kernelJob: Job? = null

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        if (kernelJob?.isActive == true) return

        _state.value = EngineState.Running
        kernelJob = scope.launch {
            while (isActive) {
                val agent = scheduler.getNext()
                if (agent != null) {
                    val budget = budgetManager.getBudget(agent.id)
                    if (budget != null && budget.tokensRemaining > 0) {
                        try {
                            val agentContext = AgentContext(
                                relevantMemories = emptyList(),
                                relevantGoals = emptyList(),
                                capabilities = agent.capabilities,
                                permissions = permissionManager.getPermissions(agent.id),
                                attention = AttentionState(emptyList()),
                                budget = budget,
                                worldState = DeviceState(emptyMap())
                            )

                            agent.perceive(agentContext)
                            agent.think(agentContext)
                            agent.plan(agentContext)
                            agent.execute(agentContext)
                        } catch (e: Exception) {
                            // Kernel survives agent crash.
                        }
                    }
                } else {
                    delay(10)
                }
            }
        }
    }

    override suspend fun stop() {
        kernelJob?.cancel()
        kernelJob = null
        _state.value = EngineState.Stopped
    }
}
