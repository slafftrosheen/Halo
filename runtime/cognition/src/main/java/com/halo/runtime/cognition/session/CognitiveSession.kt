package com.halo.runtime.cognition.session

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import com.halo.runtime.cognition.blackboard.Blackboard
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
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow<CognitiveSessionState>(CognitiveSessionState.Idle)
    val state: StateFlow<CognitiveSessionState> = _state.asStateFlow()

    private var activeJob: Job? = null

    fun submit(request: AiRequest, policy: AiContextPolicy = AiContextPolicy.CONSERVATIVE) {
        activeJob?.cancel()
        activeJob = scope.launch {
            val requestId = UUID.randomUUID().toString()
            try {
                // 1. Preparing Context
                _state.value = CognitiveSessionState.PreparingContext
                eventBus.publish(CognitiveRequestStarted(requestId = requestId))
                
                val context = buildContext(policy)
                
                // 2. Reasoning
                val providerId = providerRouter.selectedProviderId.value ?: "unknown"
                val modelId = request.modelId ?: providerRouter.selectedModelId.value
                
                val provider = providerRouter.getAllProviders().find { it.id == providerId }
                val supportsStreaming = provider?.getCapabilities()?.contains(AiCapability.STREAMING) == true

                _state.value = CognitiveSessionState.Reasoning(providerId, modelId)
                eventBus.publish(CognitiveReasoningStarted(
                    requestId = requestId,
                    providerId = providerId,
                    modelId = modelId
                ))
                
                if (supportsStreaming && provider != null) {
                    var accumulatedText = ""
                    provider.stream(request, context)
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
                                is AiStreamEvent.Failed -> throw event.error
                                else -> {}
                            }
                        }
                    
                    val finalResponse = AiResponse(text = accumulatedText, providerId = providerId, modelId = modelId)
                    _state.value = CognitiveSessionState.Completed(finalResponse)
                    eventBus.publish(CognitiveCompleted(requestId = requestId, response = accumulatedText))
                } else {
                    val response = providerRouter.execute(request, context)
                    _state.value = CognitiveSessionState.Completed(response)
                    eventBus.publish(CognitiveCompleted(requestId = requestId, response = response.text))
                }
                
            } catch (e: Exception) {
                val message = mapErrorMessage(e)
                _state.value = CognitiveSessionState.Failed(message)
                eventBus.publish(CognitiveFailed(requestId = requestId, message = message))
            }
        }
    }

    private fun mapErrorMessage(e: Exception): String {
        val msg = e.message ?: ""
        return when {
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

    private suspend fun buildContext(policy: AiContextPolicy): AiContext {
        // Aggregates data from Blackboard based on policy
        val facts = blackboard.flow.value
        
        val conversation = if (policy.includeConversation) {
            facts.filter { it.payload is com.halo.runtime.cognition.blackboard.SpeechKnowledge }
                .takeLast(10)
                .map { 
                    val speech = it.payload as com.halo.runtime.cognition.blackboard.SpeechKnowledge
                    AIMessage(Role.USER, speech.transcript) 
                }
        } else emptyList()

        return AiContext(
            conversation = conversation,
            deviceState = if (policy.includeDeviceState) com.halo.sdk.runtime.DeviceState() else null,
            recentEvents = if (policy.includeRecentEvents) emptyList() else emptyList(),
            memories = if (policy.includeMemories) emptyList() else emptyList(),
            availableTools = if (policy.includeAvailableTools) emptyList() else emptyList()
        )
    }
}
