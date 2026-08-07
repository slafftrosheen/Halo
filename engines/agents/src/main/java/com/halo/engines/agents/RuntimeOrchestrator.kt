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
import com.halo.sdk.runtime.RecoveryStarted
import com.halo.sdk.runtime.RecoveryCompleted
import com.halo.sdk.runtime.RecoveryFailed
import kotlinx.coroutines.flow.filterIsInstance
import java.util.UUID
import kotlinx.coroutines.delay

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
                    
                    // 4. Execution with Recovery
                    var currentPlan = plan
                    var currentGoal = goal
                    var executionAttempt = 0
                    var finalOutcome: Outcome = Outcome.Success("Plan executed successfully")
                    
                    while (true) {
                        executionAttempt++
                        val outcome = measureStage(observation.id.value, com.halo.sdk.runtime.RuntimeStage.EXECUTION) {
                            executionEngine.executePlan(currentPlan)
                        }
                        
                        finalOutcome = outcome
                        
                        if (outcome is com.halo.runtime.cognition.Outcome.Success || outcome is com.halo.runtime.cognition.Outcome.Partial) {
                            break
                        }
                        
                        // Handle recovery for failures
                        val recoveryContext = RecoveryContext(
                            observation = observation,
                            goal = currentGoal,
                            plan = currentPlan,
                            task = currentPlan.tasks.firstOrNull(),
                            attempt = executionAttempt,
                            deviceState = "unknown",
                            networkState = "unknown"
                        )
                        
                        val action = recoveryPolicy.determineRecovery(outcome, recoveryContext)
                        
                        // Publish RecoveryStarted event
                        eventBus.publish(RecoveryStarted(
                            observationId = observation.id.value,
                            recoveryAction = action.javaClass.simpleName,
                            attempt = executionAttempt
                        ))
                        
                        val recoverySuccess = when (action) {
                            is RecoveryAction.NotifyUser -> {
                                // In the future, this will emit a system TTS intent
                                true
                            }
                            is RecoveryAction.FallbackToRules -> {
                                // Execute fallback pipeline - for now, just try again with same plan
                                currentGoal = action.fallbackGoal
                                currentPlan = planningEngine.createPlan(currentGoal)
                                true
                            }
                            is RecoveryAction.AbortToIdle -> {
                                false
                            }
                            is RecoveryAction.RetryExecution -> {
                                if (action.delayMs > 0) {
                                    delay(action.delayMs)
                                }
                                true // Retry same plan
                            }
                        }
                        
                        // Publish RecoveryCompleted or RecoveryFailed event
                        if (recoverySuccess) {
                            eventBus.publish(RecoveryCompleted(
                                observationId = observation.id.value,
                                recoveryAction = action.javaClass.simpleName,
                                success = true
                            ))
                        } else {
                            eventBus.publish(RecoveryFailed(
                                observationId = observation.id.value,
                                recoveryAction = action.javaClass.simpleName,
                                reason = "Aborted to idle"
                            ))
                            break
                        }
                        
                        // If recovery action says don't retry, break
                        if (action is RecoveryAction.AbortToIdle) {
                            break
                        }
                        
                        // Continue loop to retry execution
                    }
                    
                    // 5. Reflection (Timeline)
                    measureStage(observation.id.value, com.halo.sdk.runtime.RuntimeStage.REFLECTION) {
                        reflectionEngine.reflectOnExecution(plan, finalOutcome)
                    }
                    
                    // 6. Signal completion
                    eventBus.publish(OutcomePublished(outcome = finalOutcome.javaClass.simpleName))
                } catch (e: Exception) {
                    val fallbackOutcome = com.halo.runtime.cognition.Outcome.PermanentFailure(e.message ?: "Pipeline crashed", e)
                    val recoveryContext = RecoveryContext(
                        observation = observation,
                        goal = null,
                        plan = null,
                        task = null,
                        attempt = 1,
                        deviceState = "unknown",
                        networkState = "unknown"
                    )
                    val action = recoveryPolicy.determineRecovery(fallbackOutcome, recoveryContext)
                    
                    eventBus.publish(RecoveryStarted(
                        observationId = observation.id.value,
                        recoveryAction = action.javaClass.simpleName,
                        attempt = 1
                    ))
                    
                    // Guaranteed fallback to idle state
                    eventBus.publish(OutcomePublished(outcome = "Failure"))
                    
                    eventBus.publish(RecoveryCompleted(
                        observationId = observation.id.value,
                        recoveryAction = action.javaClass.simpleName,
                        success = false
                    ))
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
