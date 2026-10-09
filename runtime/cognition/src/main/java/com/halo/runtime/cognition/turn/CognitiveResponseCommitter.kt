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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    val conversationId: String? = null,
    val terminal: Boolean = true,
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
    private val continuity: com.halo.runtime.cognition.continuity.InteractionContinuityCoordinator? = null,
    private val runStore: com.halo.runtime.cognition.agent.AgentRunStore? = null,
) : CognitiveResponseCommitter {

    private fun responseCommitId(runId: AgentRunId, responseId: String): String {
        return "${runId.value}:$responseId"
    }

    private val commitMutex = kotlinx.coroutines.sync.Mutex()
    private val published = LinkedHashSet<String>()
    override suspend fun commit(commit: CognitiveResponseCommit) {
        currentCoroutineContext().ensureActive()
        commitMutex.lock()
        try {
            val identity = responseCommitId(commit.runId ?: AgentRunId("ephemeral"), commit.requestId)
            if (commit.terminal && (identity in published || commit.runId?.let { runStore?.get(it)?.steps
                ?.filterIsInstance<AgentStep.ResponseCommitted>()?.any { step -> step.commitId == identity } } == true)) {
                commit.runId?.let { agentRunCoordinator?.complete(it) }
                return
            }
            commitOnce(commit)
            if (commit.terminal) {
                published.add(identity)
                while (published.size > 500) published.remove(published.first())
            }
        } finally { commitMutex.unlock() }
    }

    private suspend fun commitOnce(commit: CognitiveResponseCommit) {
        val requestId = commit.requestId
        val response = commit.response
        val interactionId = commit.interactionId
        val isHandoff = !commit.terminal || response.text.equals("HANDOFF_STARTED", ignoreCase = true) ||
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
                conversationId = commit.conversationId ?: conversationStore.activeConversationId() ?: error("Conversation unavailable"),
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
                ) + mapOf("inputSurface" to (commit.metadata["inputSurface"] ?: if (commit.outputTarget is SpeechOutputTarget.Device) "WEARABLE" else "PHONE")) + presentationMetadata
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

        currentCoroutineContext().ensureActive()
        val resolvedDelivery = if (!isHandoff) continuity?.finalDelivery(interactionId.value, commit.runId,
            com.halo.runtime.cognition.continuity.ContinuityDelivery(commit.outputTarget, commit.outputFallback, commit.responseDelivery))
            ?: com.halo.runtime.cognition.continuity.ContinuityDelivery(commit.outputTarget, commit.outputFallback, commit.responseDelivery)
            else com.halo.runtime.cognition.continuity.ContinuityDelivery(commit.outputTarget, commit.outputFallback, commit.responseDelivery)
        val runIdToCommit = commit.runId
        if (runIdToCommit != null && !isHandoff) {
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

        if (commit.terminal) eventBus.publish(
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
        // Native skills, device facts and replayed receipts are responses, not
        // model inference. Counting them as successful generations masks real
        // provider reliability in the runtime ledger.
        if (commit.terminal && response.providerId !in setOf("native", "local", "recovery")) eventBus.publish(
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
                conversationId = commit.conversationId,
                transactionId = requestId,
                delivery = resolvedDelivery.delivery,
                interactionId = interactionId.value,
                continuationPending = !commit.terminal,
                timestamp = System.currentTimeMillis()
            )
        )

        if (!isHandoff && resolvedDelivery.delivery == ResponseDelivery.SPEECH_AND_VISUAL && response.text.isNotBlank()) {
            val spokenText = TtsSanitizer.sanitize(response.text)
            if (spokenText.isNotBlank()) {
                eventBus.publish(
                    TtsRequested(
                        text = spokenText,
                        transactionId = requestId,
                        target = resolvedDelivery.target,
                        fallback = resolvedDelivery.fallback,
                        sourceCaptureId = commit.sourceCaptureId,
                        interactionId = interactionId.value,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
        }

        if (runIdToCommit != null && !isHandoff) {
            agentRunCoordinator?.complete(runIdToCommit)
        }
    }
}
