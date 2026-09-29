package com.halo.runtime.cognition.response

import com.halo.runtime.cognition.ExecutionResultPublished
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.blackboard.PlanKnowledge
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ExecutionFinished
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.RuntimeLogger
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.TtsRequested
import com.halo.sdk.runtime.ConversationTurnCompleted
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.runtime.cognition.agent.*
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CommandResponseProcess(
    private val eventBus: EventBus,
    private val blackboard: Blackboard,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val logger: RuntimeLogger? = null,
    private val mapper: CommandResponseMapper = CommandResponseMapper(),
    private val conversationStore: ConversationStore? = null,
    private val agentRunCoordinator: AgentRunCoordinator? = null,
) {
    private val pendingResults = mutableMapOf<String, MutableList<ExecutionResultPublished>>()
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return

        pendingResults.clear()
        job = scope.launch {
            // Use the single ordered event stream so ExecutionFinished cannot overtake
            // the result event that immediately precedes it on another collector.
            eventBus.events().collect { event ->
                when (event) {
                    is ExecutionResultPublished -> {
                        pendingResults.getOrPut(event.executionId) { mutableListOf() }
                            .add(event)
                    }

                    is ExecutionFinished -> {
                        val results = pendingResults.remove(event.id).orEmpty()
                        val responseText = mapper.mapExecution(results)

                        if (!responseText.isNullOrBlank()) {
                            val plan = planForId(event.planId)
                            val delivery = plan?.responseDelivery ?: ResponseDelivery.VISUAL_ONLY
                            val commitId = "deterministic-response:${event.planId}:${event.id}"
                            val truth = when {
                                results.any { it.outcome is com.halo.runtime.cognition.Outcome.Dispatched } -> ActionTruth.DISPATCHED
                                results.all { it.outcome is com.halo.runtime.cognition.Outcome.Success } -> ActionTruth.CONFIRMED
                                else -> ActionTruth.FAILED
                            }
                            val turn = plan?.let { resolvedPlan ->
                                conversationStore?.appendIfAbsent(
                                    commitId = commitId,
                                    userText = resolvedPlan.userRequest.orEmpty(),
                                    assistantText = responseText,
                                    metadata = mapOf(
                                        "interactionId" to resolvedPlan.interactionId.orEmpty(),
                                        "planId" to event.planId,
                                        "executionId" to event.id,
                                        "executionOwner" to "deterministic",
                                        "actionTruth" to truth.name,
                                        "sourceCaptureId" to resolvedPlan.sourceCaptureId.orEmpty(),
                                    ),
                                )
                            }
                            turn?.let {
                                val resolvedPlan = requireNotNull(plan)
                                eventBus.publish(ConversationTurnCompleted(
                                    turnId = it.id,
                                    userText = it.userText,
                                    assistantText = it.assistantText,
                                ))
                                resolvedPlan.agentRunId?.let(::AgentRunId)?.let { runId ->
                                    agentRunCoordinator?.recordStep(AgentStep.ResponseCommitted(
                                        AgentStepId(UUID.randomUUID().toString()), runId, Instant.now(),
                                        commitId, resolvedPlan.interactionId.orEmpty(),
                                    ))
                                    agentRunCoordinator?.complete(runId)
                                }
                                logger?.info("HALO-TXN", "HALO_ACTION_RESPONSE_COMMITTED interactionId=${resolvedPlan.interactionId} " +
                                    "runId=${resolvedPlan.agentRunId} planId=${event.planId} executionId=${event.id} truth=$truth")
                            }
                            logger?.info(
                                "HALO-TXN",
                                "execution=${event.id} plan=${event.planId} tasks=${results.size} " +
                                    "delivery=$delivery response=\"$responseText\""
                            )
                            eventBus.publish(
                                AssistantResponsePublished(
                                    text = responseText,
                                    delivery = delivery,
                                    transactionId = event.planId,
                                    interactionId = plan?.interactionId,
                                )
                            )
                            if (delivery == ResponseDelivery.SPEECH_AND_VISUAL) {
                                eventBus.publish(
                                    TtsRequested(
                                        text = responseText,
                                        transactionId = event.planId,
                                        target = plan?.speechOutputTarget ?: SpeechOutputTarget.Phone,
                                        fallback = plan?.speechOutputFallback ?: SpeechOutputFallback.ALLOW_PHONE,
                                        sourceCaptureId = plan?.sourceCaptureId,
                                        interactionId = plan?.interactionId,
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        pendingResults.clear()
    }

    private fun planForId(planId: String) =
        blackboard.flow.value.asReversed()
            .mapNotNull { knowledge -> (knowledge.payload as? PlanKnowledge)?.plan }
            .firstOrNull { it.id.toString() == planId }
}
