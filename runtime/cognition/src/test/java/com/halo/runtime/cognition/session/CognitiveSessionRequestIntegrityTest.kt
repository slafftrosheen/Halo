package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.blackboard.DefaultBlackboard
import com.halo.runtime.cognition.context.DefaultContextAssembler
import com.halo.runtime.cognition.conversation.InMemoryConversationStore
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CognitiveSessionRequestIntegrityTest {

    private class FakeEventBus : EventBus {
        val published = mutableListOf<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { published.add(event) }
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow {}
        override fun events(): Flow<HaloEvent> = flow {}
    }

    private class RecordingProvider(
        override val id: String,
        override val displayName: String,
        override val isLocal: Boolean = true,
        private val initialToolCall: ToolCall? = null,
        val finalResponseText: String = "Final output",
        val onCallAction: (() -> Unit)? = null
    ) : AiInferenceProvider {
        val capturedRequests = mutableListOf<AiRequest>()
        var callCount = 0

        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.TOOLS)

        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            capturedRequests.add(request)
            callCount++
            onCallAction?.invoke()
            return if (callCount == 1 && initialToolCall != null) {
                AiResponse(text = "", toolCalls = listOf(initialToolCall), providerId = id, modelId = request.modelId)
            } else {
                AiResponse(text = finalResponseText, providerId = id, modelId = request.modelId)
            }
        }

        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
            capturedRequests.add(request)
            callCount++
            onCallAction?.invoke()
            emit(AiStreamEvent.Started)
            if (callCount == 1 && initialToolCall != null) {
                emit(AiStreamEvent.Completed(AiResponse(text = "", toolCalls = listOf(initialToolCall), providerId = id, modelId = request.modelId)))
            } else {
                emit(AiStreamEvent.Token(finalResponseText))
                emit(AiStreamEvent.Completed(AiResponse(text = finalResponseText, providerId = id, modelId = request.modelId)))
            }
        }

        override suspend fun listModels() = emptyList<AiModel>()
    }

    private class SwitchableRouter(
        var primaryProvider: AiInferenceProvider,
        var secondaryProvider: AiInferenceProvider
    ) : AiProviderRouter {
        val selectedId = MutableStateFlow<String?>(primaryProvider.id)
        override val selectedProviderId: StateFlow<String?> = selectedId
        override val selectedModelId = MutableStateFlow<String?>("model-alpha")

        override fun selectProvider(id: String) { selectedId.value = id }
        override fun selectModel(modelId: String?) { selectedModelId.value = modelId }
        override fun getAllProviders() = listOf(primaryProvider, secondaryProvider)
        override suspend fun getEligibleProvider(request: AiRequest): AiInferenceProvider {
            val cur = selectedId.value
            return if (cur == primaryProvider.id) primaryProvider else secondaryProvider
        }
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget {
            val provider = getEligibleProvider(request)
            val model = AiModel(
                id = request.modelId ?: selectedModelId.value ?: "model-alpha",
                displayName = "Alpha Model",
                providerId = provider.id,
                inputCapabilities = setOf(AiCapability.TEXT, AiCapability.IMAGE),
                contextLength = 4096,
                supportsStreaming = true,
                supportsTools = true
            )
            return AiExecutionTarget(
                provider = provider,
                model = model,
                providerCapabilities = provider.getCapabilities()
            )
        }
        override suspend fun execute(request: AiRequest, context: AiContext): AiResponse {
            return getEligibleProvider(request).generate(request, context)
        }
        override fun start() {}
        override fun stop() {}
    }

    @Test
    fun `router selection switch during tool loop does not alter active request provider`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val store = InMemoryConversationStore()
        val registry = ToolRegistry(listOf(EchoToolExecutor()))
        val authorizer = InMemoryToolAuthorizer(registry)

        val toolCall = ToolCall(id = "tc1", functionName = "halo.echo", arguments = mapOf("text" to "hi"))
        lateinit var router: SwitchableRouter
        lateinit var p1: RecordingProvider
        lateinit var p2: RecordingProvider

        p1 = RecordingProvider("p1", "Provider 1", initialToolCall = toolCall, finalResponseText = "P1 Done", onCallAction = {
            if (p1.callCount == 1) {
                // Switch router selection to P2 while P1 is processing tool call
                router.selectProvider("p2")
            }
        })
        p2 = RecordingProvider("p2", "Provider 2", finalResponseText = "P2 Done")
        router = SwitchableRouter(p1, p2)

        val assembler = DefaultContextAssembler(store, blackboard, registry)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            toolRegistry = registry,
            toolAuthorizer = authorizer,
            scope = this
        )

        // Submit request targeting P1
        session.submit(AiRequest(text = "Run tool"))
        advanceUntilIdle()

        // P1 should be called twice (initial tool call + tool follow-up generation) despite router selection changing to P2
        assertEquals(2, p1.callCount)
        assertEquals(0, p2.callCount)
        assertEquals(1, store.turns.value.size)
        assertEquals("P1 Done", store.turns.value[0].assistantText)
    }

    @Test
    fun `cognitive session uses model ID from resolved target rather than router selection`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val store = InMemoryConversationStore()
        val provider = RecordingProvider("p1", "Provider 1", finalResponseText = "Output")
        
        val router = object : AiProviderRouter {
            override val selectedProviderId = MutableStateFlow<String?>("p1")
            override val selectedModelId = MutableStateFlow<String?>("router-model-id")
            override fun selectProvider(id: String) {}
            override fun selectModel(modelId: String?) {}
            override fun getAllProviders() = listOf(provider)
            override suspend fun getEligibleProvider(request: AiRequest) = provider
            override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget {
                val targetModel = AiModel(
                    id = "target-model-id",
                    displayName = "Target Model",
                    providerId = provider.id,
                    inputCapabilities = setOf(AiCapability.TEXT),
                    contextLength = 4096,
                    supportsStreaming = true,
                    supportsTools = false
                )
                return AiExecutionTarget(
                    provider = provider,
                    model = targetModel,
                    providerCapabilities = setOf(AiCapability.TEXT, AiCapability.STREAMING)
                )
            }
            override suspend fun execute(request: AiRequest, context: AiContext) = provider.generate(request, context)
            override fun start() {}
            override fun stop() {}
        }

        val assembler = DefaultContextAssembler(store, blackboard)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            scope = this
        )

        session.submit(AiRequest(text = "Hello"))
        advanceUntilIdle()

        assertEquals(1, provider.capturedRequests.size)
        // Request sent to provider must use target.model.id ("target-model-id"), NOT router.selectedModelId ("router-model-id")
        assertEquals("target-model-id", provider.capturedRequests[0].modelId)
    }

    @Test
    fun `unauthorized tool is rejected and not authorized`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val store = InMemoryConversationStore()
        val registry = ToolRegistry(listOf(EchoToolExecutor()))
        // Authorizer denies all tools
        val authorizer = object : ToolAuthorizer {
            override suspend fun authorize(toolCall: ToolCall) = ToolAuthorization.Rejected(toolCall, "Denied by security policy")
        }

        val toolCall = ToolCall(id = "tc1", functionName = "halo.echo", arguments = mapOf("text" to "hi"))
        val provider = RecordingProvider("p1", "P1", initialToolCall = toolCall, finalResponseText = "Denied handled")
        val router = SwitchableRouter(provider, provider)

        val assembler = DefaultContextAssembler(store, blackboard, registry)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            toolRegistry = registry,
            toolAuthorizer = authorizer,
            scope = this
        )

        session.submit(AiRequest(text = "Run tool"))
        advanceUntilIdle()

        assertTrue(eventBus.published.any { it is ToolCallRequested })
        assertTrue(eventBus.published.any { it is ToolCallRejected && it.reason.contains("Denied") })
        assertFalse(eventBus.published.any { it is ToolCallAuthorized })
    }
}
