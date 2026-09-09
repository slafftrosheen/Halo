package com.halo.runtime.cognition.session

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.halo.runtime.cognition.blackboard.Blackboard
import com.halo.runtime.cognition.conversation.ConversationStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages the lifecycle of a single cognitive interaction.
 */
class CognitiveSession(
    private val eventBus: EventBus,
    private val blackboard: Blackboard,
    private val providerRouter: AiProviderRouter,
    private val conversationStore: ConversationStore,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<CognitiveSessionState>(CognitiveSessionState.Idle)
    val state: StateFlow<CognitiveSessionState> = _state.asStateFlow()

    private var activeJob: Job? = null

    init {
        scope.launch {
            eventBus.observe(SpeechRecognized::class.java).collect { speech ->
                if (activeJob?.isActive == true) {
                    println("[CognitiveSession] Ignoring speech during active AI generation")
                    return@collect
                }
                val text = speech.text.trim()
                if (text.isNotEmpty()) {
                    println("[CognitiveSession] Auto-submitting recognized speech")
                    submit(AiRequest(text = text))
                }
            }
        }
    }

    fun submit(request: AiRequest, policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE) {
        println("[CognitiveSession] submit request")
        activeJob?.cancel()
        activeJob = scope.launch {
            val requestId = UUID.randomUUID().toString()
            try {
                var finalRequest = request
                if (finalRequest.image == null) {
                    val latestObsKnowledge = blackboard.flow.value
                        .mapNotNull { it.payload as? com.halo.runtime.cognition.blackboard.ObservationKnowledge }
                        .map { it.observation }
                        .findLast { it.payload is ImageObservationPayload }
                    
                    if (latestObsKnowledge != null && (System.currentTimeMillis() - latestObsKnowledge.timestamp) < 60_000L) {
                        val imgPayload = latestObsKnowledge.payload as ImageObservationPayload
                        if (imgPayload.imageBytes.isNotEmpty()) {
                            finalRequest = finalRequest.copy(
                                image = imgPayload.imageBytes,
                                mimeTypes = listOf(imgPayload.mimeType)
                            )
                            println("[CognitiveSession] Attached image observation (${imgPayload.imageBytes.size} bytes) to request")
                        }
                    }
                }

                // 1. Provider Eligibility Check (Fail Fast)
                val provider = providerRouter.getEligibleProvider(finalRequest)

                val providerId = provider.id
                val modelId = finalRequest.modelId ?: providerRouter.selectedModelId.value
                
                // 2. Preparing Context
                println("[CognitiveSession] Preparing context")
                _state.value = CognitiveSessionState.PreparingContext
                eventBus.publish(CognitiveRequestStarted(requestId = requestId))
                
                val context = buildContext(policy)
                
                // 3. Reasoning
                println("[CognitiveSession] Provider: $providerId, Model: $modelId")
                val supportsStreaming = provider.getCapabilities().contains(AiCapability.STREAMING)

                _state.value = CognitiveSessionState.Reasoning(providerId, modelId)
                eventBus.publish(CognitiveReasoningStarted(
                    requestId = requestId,
                    providerId = providerId,
                    modelId = modelId
                ))
                
                if (supportsStreaming) {
                    println("[CognitiveSession] Starting streaming")
                    var completedResponse: AiResponse? = null
                    var accumulatedText = ""

                    provider.stream(finalRequest, context)
                        .collect { event ->
                            when (event) {
                                is AiStreamEvent.Started -> {
                                    _state.value = CognitiveSessionState.Streaming("")
                                }
                                is AiStreamEvent.Token -> {
                                    accumulatedText += event.text
                                    _state.value = CognitiveSessionState.Streaming(accumulatedText)
                                    eventBus.publish(CognitiveTokenReceived(requestId = requestId, token = event.text))
                                }
                                is AiStreamEvent.Completed -> {
                                    completedResponse = event.response
                                }
                                is AiStreamEvent.Failed -> throw event.error
                                is AiStreamEvent.ToolCallDelta -> {}
                            }
                        }

                    val response = completedResponse
                        ?: throw AiProviderException(
                            AiErrorType.INVALID_RESPONSE,
                            "Provider stream ended without Completed event"
                        )

                    if (response.text.isBlank() && response.toolCalls.isEmpty()) {
                        throw AiProviderException(
                            AiErrorType.EMPTY_RESPONSE,
                            "Provider stream completed with no usable response"
                        )
                    }

                    _state.value = CognitiveSessionState.Completed(response)
                    eventBus.publish(CognitiveCompleted(requestId = requestId, response = response.text))

                    val responseText = response.text.trim()
                    val userText = finalRequest.text?.trim().orEmpty()
                    if (responseText.isNotBlank() && responseText != "HANDOFF_STARTED") {
                        if (userText.isNotBlank()) {
                            val turn = conversationStore.append(
                                userText = userText,
                                assistantText = responseText
                            )
                            eventBus.publish(
                                ConversationTurnCompleted(
                                    turnId = turn.id,
                                    userText = turn.userText,
                                    assistantText = turn.assistantText
                                )
                            )
                        }
                        eventBus.publish(TtsRequested(text = responseText, timestamp = System.currentTimeMillis()))
                    }
                    println("[CognitiveSession] Streaming completed")
                } else {
                    println("[CognitiveSession] Executing one-shot")
                    val response = providerRouter.execute(finalRequest, context)
                    if (response.text.isBlank() && response.toolCalls.isEmpty()) {
                        throw AiProviderException(
                            AiErrorType.EMPTY_RESPONSE,
                            "Provider returned no usable response"
                        )
                    }
                    _state.value = CognitiveSessionState.Completed(response)
                    eventBus.publish(CognitiveCompleted(requestId = requestId, response = response.text))

                    val responseText = response.text.trim()
                    val userText = finalRequest.text?.trim().orEmpty()
                    if (responseText.isNotBlank() && responseText != "HANDOFF_STARTED") {
                        if (userText.isNotBlank()) {
                            val turn = conversationStore.append(
                                userText = userText,
                                assistantText = responseText
                            )
                            eventBus.publish(
                                ConversationTurnCompleted(
                                    turnId = turn.id,
                                    userText = turn.userText,
                                    assistantText = turn.assistantText
                                )
                            )
                        }
                        eventBus.publish(TtsRequested(text = responseText, timestamp = System.currentTimeMillis()))
                    }
                    println("[CognitiveSession] Execution completed")
                }
            } catch (e: CancellationException) {
                _state.value = CognitiveSessionState.Idle
                throw e
            } catch (e: Exception) {
                println("[CognitiveSession] Session failed: ${e.message}")
                val message = mapErrorMessage(e)
                _state.value = CognitiveSessionState.Failed(message)
                eventBus.publish(CognitiveFailed(requestId = requestId, message = message))
            }
        }
    }

    private fun mapErrorMessage(e: Exception): String {
        val msg = e.message ?: ""
        return when {
            e is AiProviderException -> msg
            msg.contains("401") -> "Invalid API key"
            msg.contains("403") -> "Access denied"
            msg.contains("429") -> "Provider rate limit exceeded"
            msg.contains("Unable to resolve host") -> "Cannot reach provider"
            else -> "Intelligence provider failed: ${e.localizedMessage}"
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _state.value = CognitiveSessionState.Idle
    }

    fun clearConversation() {
        scope.launch {
            conversationStore.clear()
        }
    }

    private fun buildContext(policy: AiContextPolicy): AiContext {
        val conversation = if (policy.includeConversation) {
            conversationStore.recentMessages(maxTurns = 10)
        } else {
            emptyList()
        }

        return AiContext(
            conversation = conversation,
            deviceState = if (policy.includeDeviceState) com.halo.sdk.runtime.DeviceState() else null,
            recentEvents = if (policy.includeRecentEvents) emptyList() else emptyList(),
            memories = if (policy.includeMemories) emptyList() else emptyList(),
            availableTools = if (policy.includeAvailableTools) emptyList() else emptyList()
        )
    }
}
