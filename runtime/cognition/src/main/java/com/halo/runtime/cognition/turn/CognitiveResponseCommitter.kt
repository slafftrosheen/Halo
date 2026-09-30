package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunCoordinator
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.agent.AgentStep
import com.halo.runtime.cognition.agent.AgentStepId
import com.halo.runtime.cognition.conversation.ConversationStore
import com.halo.runtime.cognition.interaction.PresentationCommitRegistry
import com.halo.runtime.cognition.interaction.PresentationMetadata
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
    /**
     * MINING-P2-001: carries a validated structured presentation produced during this run into the
     * committed turn, and nothing more. Text stays the semantic truth; the payload is a bounded view
     * that context assembly never reads back.
     */
    private val presentationCommits: PresentationCommitRegistry? = null,
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

        /*
         * Presentation metadata is resolved exactly once per commit. A handoff consumes and drops it:
         * the continuing run owns the surface, and the committed turn must never carry a card the
         * assistant never actually finished with.
         */
        val recordedPresentation = presentationCommits?.consume(commit.runId?.value, interactionId.value)
        val presentationMetadata = if (isHandoff || recordedPresentation == null) {
            emptyMap()
        } else {
            PresentationMetadata.metadataFor(
                presentation = recordedPresentation.presentation,
                template = recordedPresentation.template,
                nodeCount = recordedPresentation.nodeCount,
            ) + PresentationMetadata.provenanceMetadata(
                runId = commit.runId?.value,
                interactionId = interactionId.value,
                providerId = response.providerId,
                modelId = response.modelId,
                createdAtMs = System.currentTimeMillis(),
            )
        }

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
                    "requestId" to requestId,
                    "interactionId" to interactionId.value,
                    "runId" to (commit.runId?.value ?: ""),
                    "sourceCaptureId" to (commit.sourceCaptureId ?: "")
                ) + presentationMetadata
            )
        } else null

        if (turn != null) {
            println(
                "HALO_RESPONSE_COMMITTED conversationId=${turn.conversationId} turnId=${turn.id} " +
                    "interactionId=${interactionId.value} requestId=$requestId " +
                    "source=COMMITTED_CONVERSATION textLength=${response.text.length}",
            )
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
                backend = response.executionBackend,
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
                interactionId = interactionId.value,
                timestamp = System.currentTimeMillis()
            )
        )

        if (!isHandoff && commit.responseDelivery == ResponseDelivery.SPEECH_AND_VISUAL && response.text.isNotBlank()) {
            val spokenText = TtsSanitizer.sanitize(response.text)
            if (spokenText.isNotBlank()) {
                eventBus.publish(
                    TtsRequested(
                        text = spokenText,
                        transactionId = requestId,
                        target = commit.outputTarget,
                        fallback = commit.outputFallback,
                        sourceCaptureId = commit.sourceCaptureId,
                        interactionId = interactionId.value,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        if (runIdToCommit != null) {
            agentRunCoordinator?.complete(runIdToCommit)
        }
    }
}
