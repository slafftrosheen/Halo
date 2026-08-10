package com.halo.engines.agents

import com.halo.engines.execution.ExecutionEngine
import com.halo.engines.memory.ReflectionEngine
import com.halo.engines.planning.PlanningEngine
import com.halo.icl.ontology.Goal
import com.halo.runtime.cognition.GoalCreated
import com.halo.runtime.cognition.Outcome
import com.halo.runtime.cognition.OutcomePublished
import com.halo.runtime.cognition.RecoveryAction
import com.halo.runtime.cognition.RecoveryCompleted
import com.halo.runtime.cognition.RecoveryContext
import com.halo.runtime.cognition.RecoveryFailed
import com.halo.runtime.cognition.RecoveryStarted
import com.halo.runtime.cognition.ExecutionPlan
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.RuntimeStage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates:
 *
 *     GoalCreated
 *        ↓
 *     Planning
 *        ↓
 *     Execution
 *        ↓
 *     Recovery
 *        ↓
 *     Reflection
 *        ↓
 *     Outcome
 *
 * It deliberately does NOT:
 *
 * - interpret speech
 * - generate intents
 * - generate goals
 * - resolve capabilities
 */
class RuntimeOrchestrator(
    private val planningEngine: PlanningEngine,
    private val executionEngine: ExecutionEngine,
    private val reflectionEngine: ReflectionEngine,
    private val eventBus: EventBus,
    private val recoveryPolicy: RecoveryPolicy = DefaultRecoveryPolicy()
) {

    private val scope =
        CoroutineScope(Dispatchers.Default)

    private var job: Job? = null

    fun start() {

        if (job?.isActive == true) {
            return
        }

        job = scope.launch {

            eventBus
                .observe(GoalCreated::class.java)
                .collect { event ->

                    processGoal(
                        event.goal
                    )
                }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun processGoal(
        goal: Goal
    ) {

        val traceId =
            goal.provenance.parentId
                ?: goal.id.value.toString()

        try {

            /*
             * PLAN
             */
            val plan =
                measureStage(
                    traceId,
                    RuntimeStage.PLANNING
                ) {
                    planningEngine.createPlan(goal)
                }

            /*
             * EXECUTION + RECOVERY
             */
            var currentPlan =
                plan

            var executionAttempt =
                0

            var finalOutcome: com.halo.runtime.cognition.Outcome =
                com.halo.runtime.cognition.Outcome.Success(
                    "Plan executed successfully"
                )

            while (true) {

                executionAttempt++

                val outcome =
                    measureStage(
                        traceId,
                        RuntimeStage.EXECUTION
                    ) {
                        executionEngine.executePlan(
                            currentPlan
                        )
                    }

                finalOutcome =
                    outcome

                if (
                    outcome is com.halo.runtime.cognition.Outcome.Success ||
                    outcome is com.halo.runtime.cognition.Outcome.Partial
                ) {
                    break
                }

                val context =
                    RecoveryContext(
                        goal = goal,
                        plan = currentPlan,
                        task = currentPlan.tasks.firstOrNull(),
                        attempt = executionAttempt,
                        deviceState = null,
                        networkState = null
                    )

                val action =
                    recoveryPolicy.determineRecovery(
                        outcome,
                        context
                    )

                eventBus.publish(
                    RecoveryStarted(
                        observationId = traceId,
                        recoveryAction =
                            action::class.simpleName
                                ?: "Unknown",
                        attempt = executionAttempt
                    )
                )

                when (action) {

                    is RecoveryAction.RetryExecution -> {

                        if (action.delayMs > 0) {
                            delay(action.delayMs)
                        }

                        eventBus.publish(
                            RecoveryCompleted(
                                observationId = traceId,
                                recoveryAction =
                                    action::class.simpleName
                                        ?: "Unknown",
                                success = true
                            )
                        )
                    }

                    is RecoveryAction.FallbackToRules -> {

                        currentPlan =
                            planningEngine.createPlan(
                                action.fallbackGoal
                            )

                        eventBus.publish(
                            RecoveryCompleted(
                                observationId = traceId,
                                recoveryAction =
                                    action::class.simpleName
                                        ?: "Unknown",
                                success = true
                            )
                        )
                    }

                    is RecoveryAction.NotifyUser -> {

                        /*
                         * Notification/TTS capability is not part of
                         * this orchestration layer.
                         *
                         * For now the recovery result is terminal.
                         */
                        eventBus.publish(
                            RecoveryCompleted(
                                observationId = traceId,
                                recoveryAction =
                                    action::class.simpleName
                                        ?: "Unknown",
                                success = false
                            )
                        )

                        break
                    }

                    is RecoveryAction.AbortToIdle -> {

                        eventBus.publish(
                            RecoveryFailed(
                                observationId = traceId,
                                recoveryAction =
                                    action::class.simpleName
                                        ?: "Unknown",
                                reason =
                                    "Recovery aborted"
                            )
                        )

                        break
                    }
                }
            }

            /*
             * REFLECTION
             */
            measureStage(
                traceId,
                RuntimeStage.REFLECTION
            ) {
                reflectionEngine.reflectOnExecution(
                    plan,
                    finalOutcome
                )
            }

            eventBus.publish(
                OutcomePublished(
                    outcome =
                        finalOutcome::class.simpleName
                            ?: "Unknown"
                )
            )

        } catch (exception: Exception) {

            finalFailure(
                traceId = traceId,
                exception = exception
            )
        }
    }

    private suspend fun finalFailure(
        traceId: String,
        exception: Exception
    ) {

        val outcome =
            com.halo.runtime.cognition.Outcome.PermanentFailure(
                reason = exception.message ?: "Runtime orchestration failed",
                error = exception
            )

        eventBus.publish(
            RecoveryFailed(
                observationId = traceId,
                recoveryAction = "RuntimeFailure",
                reason = exception.message ?: "Runtime orchestration failed"
            )
        )

        eventBus.publish(
            OutcomePublished(
                outcome =
                    outcome::class.simpleName ?: "PermanentFailure"
            )
        )
    }

    private suspend fun <T> measureStage(
        traceId: String,
        stage: RuntimeStage,
        block: suspend () -> T
    ): T {
        /*
         * Keep timing centralized.
         *
         * The SDK/runtime metrics layer can replace this later without
         * changing the cognitive pipeline.
         */
        return block()
    }
}