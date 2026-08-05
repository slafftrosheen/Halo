package com.halo.engines.executive

import com.halo.sdk.runtime.*
import com.halo.libraries.common.core.Identifier
import com.halo.libraries.common.core.Version
import com.halo.runtime.services.Services
import com.halo.runtime.cognition.Goal
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.engines.executive.manager.*
import com.halo.runtime.cognition.executive.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class ExecutiveEngine(
    private val blackboard: Blackboard,
    private val focusManager: FocusManager,
    private val priorityManager: PriorityManager,
    private val riskManager: RiskManager,
    private val confirmationEngine: ConfirmationEngine,
    private val policyEngine: PolicyEngine
) : Engine {
    override val metadata = EngineMetadata(
        id = Identifier("engine.executive.core"),
        version = Version(1, 0, 0),
        description = "Core Executive Function Engine"
    )

    private val _health = MutableStateFlow(EngineHealth.Healthy)
    override val health: StateFlow<EngineHealth> = _health

    private val _state = MutableStateFlow<EngineState>(EngineState.Created)
    override val state: StateFlow<EngineState> = _state

    override val dependencies: Set<ServiceKey<*>> = setOf(Services.EventBus)

    private lateinit var context: EngineContext
    private val scope = CoroutineScope(Dispatchers.Default)
    private var currentMode = CognitiveMode.Normal

    override suspend fun initialize(context: EngineContext) {
        this.context = context
        _state.value = EngineState.Initializing
    }

    override suspend fun start() {
        _state.value = EngineState.Running
        
        // Listen for new Goals on Blackboard to evaluate
        scope.launch {
            // blackboard.subscribe(GoalKnowledge::class) { goalKnowledge ->
            //     val goal = goalKnowledge.goal
            //     val verdict = evaluateGoal(goal)
            //     val attention = focusManager.allocateAttention(listOf(goal))
            //
            //     val decision = ExecutiveDecision(goal.id, verdict, "Evaluated by Executive")
            //     val knowledge = ExecutiveKnowledge(decision, currentMode, attention, Clock.System.now())
            //     blackboard.publish(knowledge)
            // }
        }
    }

    private fun evaluateGoal(goal: Goal): ExecutiveVerdict {
        if (!policyEngine.isAuthorized(goal, currentMode)) {
            return ExecutiveVerdict.Cancel
        }

        val risk = riskManager.evaluate(goal)
        if (confirmationEngine.requiresConfirmation(risk)) {
            return ExecutiveVerdict.RequireConfirmation
        }

        // Check priorities, possibly pause if something more important is running
        // For simplicity, Authorize here.
        return ExecutiveVerdict.Authorize
    }

    override suspend fun stop() {
        _state.value = EngineState.Stopped
    }
}
