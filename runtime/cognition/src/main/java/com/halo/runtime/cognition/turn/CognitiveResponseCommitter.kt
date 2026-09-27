package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.CognitiveCompleted
import com.halo.sdk.runtime.CognitiveGenerationMetrics
import com.halo.sdk.runtime.ConversationTurnCompleted
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ResponseDelivery
import com.halo.sdk.runtime.RuntimeMetrics
import com.halo.sdk.runtime.SpeechOutputFallback
import com.halo.sdk.runtime.SpeechOutputTarget
import com.halo.sdk.runtime.TtsRequested
import java.time.Instant
import java.util.UUID

data class CognitiveResponseCommit(
    val runId: AgentRunId?,
    val interactionId: InteractionId,
    val requestId: String,
    val userText: String,
    val response: AiResponse,
    val responseDelivery: ResponseDelivery,
    val outputTarget: SpeechOutputTarget,
    val outputFallback: SpeechOutputFallback,
    val sourceCaptureId: String?,
    val metadata: Map<String, String>,
)

interface CognitiveResponseCommitter {
    suspend fun commit(commit: CognitiveResponseCommit)
}

class DefaultCognitiveResponseCommitter(
    private val conversationStore: ConversationStore,
    private val agentRunCoordinator: AgentRunCoordinator?,
    private val eventBus: EventBus,
) : CognitiveResponseCommitter {

    private fun responseCommitId(runId: AgentRunId, responseId: String): String {
        return "${runId.value}:$responseId"
    }

    override suspend fun commit(commit: CognitiveResponseCommit) {
        val requestId = commit.requestId
        val response = commit.response
        val interactionId = commit.interactionId
        val isHandoff = response.text.equals("HANDOFF_STARTED", ignoreCase = true) ||
            response.finishReason.equals("handoff", ignoreCase = true)
        
        val turn = if (!isHandoff) {
            conversationStore.appendIfAbsent(
                commitId = responseCommitId(commit.runId ?: AgentRunId("ephemeral"), requestId),
                userText = commit.userText,
                assistantText = response.text,
                metadata = mapOf(
                    "providerId" to response.providerId,
                    "modelId" to (response.modelId ?: ""),
                    "requestedModelId" to (response.requestedModelId ?: response.modelId ?: ""),
                    "resolvedModelId" to (response.resolvedModelId ?: response.modelId ?: ""),
                    "interactionId" to interactionId.value,
                    "sourceCaptureId" to (commit.sourceCaptureId ?: "")
                )
            )
        } else null

        if (turn != null) {
            eventBus.publish(
                ConversationTurnCompleted(
                    turnId = turn.id,
                    userText = commit.userText,
                    assistantText = response.text,
                )
            )
        }

        val runIdToCommit = commit.runId
        if (runIdToCommit != null) {
            agentRunCoordinator?.recordStep(
                AgentStep.ResponseCommitted(
                    AgentStepId(UUID.randomUUID().toString()),
                    runIdToCommit,
                    Instant.now(),
                    commitId = responseCommitId(runIdToCommit, requestId),
                    interactionId = interactionId.value,
                )
            )
        }

        eventBus.publish(
            CognitiveCompleted(
                requestId = requestId,
                response = response.text,
                interactionId = interactionId.value,
                metrics = response.metrics?.let {
                    CognitiveGenerationMetrics(
                        startedAtMs = it.startedAtMs,
                        firstTokenAtMs = it.firstTokenAtMs,
                        completedAtMs = it.completedAtMs,
                        inputTokens = it.inputTokens,
                        outputTokens = it.outputTokens,
                    )
                }
            )
        )
        eventBus.publish(
            com.halo.sdk.runtime.InferenceObserved(
                requestId = requestId,
                providerId = response.providerId,
                requestedModelId = response.requestedModelId,
                resolvedModelId = response.resolvedModelId,
                success = true,
                metrics = response.metrics?.let {
                    CognitiveGenerationMetrics(it.startedAtMs, it.firstTokenAtMs, it.completedAtMs, it.inputTokens, it.outputTokens)
                },
            )
        )
        
        eventBus.publish(
            AssistantResponsePublished(
                text = response.text,
                transactionId = requestId,
                delivery = commit.responseDelivery,
                timestamp = System.currentTimeMillis()
            )
        )

        if (!isHandoff && commit.responseDelivery == ResponseDelivery.SPEECH_AND_VISUAL && response.text.isNotBlank()) {
            eventBus.publish(
                TtsRequested(
                    text = response.text,
                    transactionId = requestId,
                    target = commit.outputTarget,
                    fallback = commit.outputFallback,
                    sourceCaptureId = commit.sourceCaptureId,
                    interactionId = interactionId.value,
                    timestamp = System.currentTimeMillis()
                )
            )
        }

        if (runIdToCommit != null) {
            agentRunCoordinator?.complete(runIdToCommit)
        }
    }
}
