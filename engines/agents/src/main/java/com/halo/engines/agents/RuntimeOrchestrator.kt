package com.halo.engines.agents

import com.halo.engines.interpretation.internal.DefaultInterpretationEngine
import com.halo.engines.reasoning.service.GoalManager
import com.halo.engines.planning.PlanningEngine
import com.halo.engines.execution.ExecutionEngine
import com.halo.engines.memory.ReflectionEngine
import com.halo.engines.speech.SpeechRecognizedPayload
import com.halo.runtime.cognition.Outcome
import com.halo.sdk.runtime.Observation
import com.halo.sdk.runtime.ObservationId
import com.halo.sdk.runtime.Source
import com.halo.sdk.runtime.Modality
import com.halo.icl.ontology.*
import com.halo.cognition.language.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ObservationCreated
import com.halo.sdk.runtime.OutcomePublished
import kotlinx.coroutines.flow.filterIsInstance
import java.util.UUID

/**
 * The RuntimeOrchestrator coordinates the Alpha Loop deterministically:
 * Speech Event -> Interpretation -> Goal Manager -> Planning -> Execution -> Outcome -> Timeline
 * 
 * Subscribes to the EventBus directly instead of being invoked.
 */
class RuntimeOrchestrator(
    private val interpretationEngine: DefaultInterpretationEngine,
    private val goalManager: GoalManager,
    private val planningEngine: PlanningEngine,
    private val executionEngine: ExecutionEngine,
    private val reflectionEngine: ReflectionEngine,
    private val eventBus: EventBus,
    private val recoveryPolicy: RecoveryPolicy = DefaultRecoveryPolicy()
) {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun start() {
        scope.launch {
            eventBus.observe(ObservationCreated::class.java).collect { event ->
                val observation = event.observation
                
                try {
                    // 1. Interpretation
                    val result = measureStage(observation.id.value, com.halo.sdk.runtime.RuntimeStage.INTERPRETATION) {
                        interpretationEngine.interpret(observation)
                    }
                    
                    // 2. Goal Generation
                    val goal = measureStage(observation.id.value, com.halo.sdk.runtime.RuntimeStage.GOAL_GENERATION) {
                        val intent = result.interpretation.intent
                        val payload = when (intent) {
                            is CallIntent -> CommunicationPayload(target = extractEntityValue(result.interpretation.entities) ?: "")
                            is NavigateIntent -> NavigationPayload(destination = extractEntityValue(result.interpretation.entities) ?: "")
                            is PlayIntent -> MediaPayload(query = extractEntityValue(result.interpretation.entities) ?: "")
                            is OpenIntent -> DevicePayload(action = "open", target = extractEntityValue(result.interpretation.entities) ?: "")
                            else -> {
                                val text = (observation.payload as? SpeechRecognizedPayload)?.transcript ?: "General"
                                ConversationPayload(topic = text)
                            }
                        }
                        val type = when (intent) {
                            is CallIntent -> GoalType.COMMUNICATION
                            is NavigateIntent -> GoalType.NAVIGATION
                            is PlayIntent -> GoalType.MEDIA
                            is OpenIntent -> GoalType.DEVICE
                            else -> GoalType.CONVERSATION
                        }
                        goalManager.createGoal(type, intent, payload, Priority.NORMAL, result.interpretation.confidence)
                    }
                    
                    // 3. Planning
                    val plan = measureStage(observation.id.value, com.halo.sdk.runtime.RuntimeStage.PLANNING) {
                        planningEngine.createPlan(goal)
                    }
                    
                    // 4. Execution
                    val outcome = measureStage(observation.id.value, com.halo.sdk.runtime.RuntimeStage.EXECUTION) {
                        executionEngine.executePlan(plan)
                    }
                    
                    if (outcome is com.halo.runtime.cognition.Outcome.PermanentFailure || 
                        outcome is com.halo.runtime.cognition.Outcome.RetryableFailure || 
                        outcome is com.halo.runtime.cognition.Outcome.Cancelled) {
                        
                        val action = recoveryPolicy.determineRecovery(outcome, com.halo.sdk.runtime.RuntimeStage.EXECUTION)
                        when (action) {
                            is RecoveryAction.NotifyUser -> {
                                // In the future, this will emit a system TTS intent
                            }
                            is RecoveryAction.FallbackToRules -> {
                                // Execute fallback pipeline
                            }
                            is RecoveryAction.AbortToIdle -> {
                                // Abort
                            }
                        }
                    }
                    
                    // 5. Reflection (Timeline)
                    measureStage(observation.id.value, com.halo.sdk.runtime.RuntimeStage.REFLECTION) {
                        reflectionEngine.reflectOnExecution(plan, outcome)
                    }
                    
                    // 6. Signal completion
                    eventBus.publish(OutcomePublished(outcome = outcome.javaClass.simpleName))
                } catch (e: Exception) {
                    val fallbackOutcome = com.halo.runtime.cognition.Outcome.PermanentFailure(e.message ?: "Pipeline crashed", e)
                    val action = recoveryPolicy.determineRecovery(fallbackOutcome, com.halo.sdk.runtime.RuntimeStage.EXECUTION)
                    // Guaranteed fallback to idle state
                    eventBus.publish(OutcomePublished(outcome = "Failure"))
                }
            }
        }
    }

    private suspend fun <T> measureStage(
        observationId: String, 
        stage: com.halo.sdk.runtime.RuntimeStage, 
        block: suspend () -> T
    ): T {
        val startedAt = System.currentTimeMillis()
        var success = true
        var errorCause: String? = null
        try {
            return block()
        } catch (e: Exception) {
            success = false
            errorCause = e::class.simpleName
            throw e
        } finally {
            val finishedAt = System.currentTimeMillis()
            val latency = finishedAt - startedAt
            eventBus.publish(
                com.halo.sdk.runtime.StageMetricsPublished(
                    observationId = observationId,
                    stage = stage,
                    startedAt = startedAt,
                    finishedAt = finishedAt,
                    latency = latency,
                    success = success,
                    error = errorCause
                )
            )
        }
    }

    private fun extractEntityValue(entities: List<Entity>): String? {
        val first = entities.firstOrNull() ?: return null
        return when (first) {
            is PersonEntity -> first.name
            is LocationEntity -> first.location
            is PlaceEntity -> first.name
            is ObjectEntity -> first.name
            is ApplicationEntity -> first.name
            is DeviceEntity -> first.name
            is MediaEntity -> first.title
            is WebEntity -> first.query
            is TimeEntity -> first.expression
            is DateEntity -> first.expression
            is NumberEntity -> first.value.toString()
        }
    }
}
