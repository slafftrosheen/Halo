package com.halo.runtime.cognition.session

import com.halo.runtime.cognition.blackboard.DefaultBlackboard
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
class CognitiveSessionToolTest {

    private class FakeEventBus : EventBus {
        val published = mutableListOf<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { published.add(event) }
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow {}
        override fun events(): Flow<HaloEvent> = flow {}
    }

    private class ToolCallingProvider(
        override val id: String = "fake.tool.provider",
        override val displayName: String = "Fake Tool Provider",
        override val isLocal: Boolean = true,
        val supportsToolsCapability: Boolean = true,
        val initialToolCalls: List<ToolCall> = emptyList(),
        val finalResponseText: String = "Echo completed."
    ) : AiInferenceProvider {
        val capturedContexts = mutableListOf<AiContext>()
        var callCount = 0

        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = if (supportsToolsCapability) {
            setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.TOOLS)
        } else {
            setOf(AiCapability.TEXT, AiCapability.STREAMING)
        }

        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            capturedContexts.add(context)
            callCount++
            return if (callCount == 1 && initialToolCalls.isNotEmpty()) {
                AiResponse(text = "", toolCalls = initialToolCalls, providerId = id)
            } else {
                AiResponse(text = finalResponseText, providerId = id)
            }
        }

        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
            capturedContexts.add(context)
            callCount++
            emit(AiStreamEvent.Started)
            if (callCount == 1 && initialToolCalls.isNotEmpty()) {
                emit(AiStreamEvent.Completed(AiResponse(text = "", toolCalls = initialToolCalls, providerId = id)))
            } else {
                emit(AiStreamEvent.Token(finalResponseText))
                emit(AiStreamEvent.Completed(AiResponse(text = finalResponseText, providerId = id)))
            }
        }

        override suspend fun listModels() = emptyList<AiModel>()
    }

    private class FakeRouter(private val provider: AiInferenceProvider) : AiProviderRouter {
        override val selectedProviderId = MutableStateFlow<String?>(provider.id)
        override val selectedModelId = MutableStateFlow<String?>("test-model")
        override fun selectProvider(id: String) {}
        override fun selectModel(modelId: String?) {}
        override fun getAllProviders() = listOf(provider)
        override suspend fun getEligibleProvider(request: AiRequest) = provider
        override suspend fun resolveExecutionTarget(request: AiRequest, workload: AiWorkloadSpec): AiExecutionTarget {
            val model = AiModel(
                id = request.modelId ?: selectedModelId.value ?: "test-model",
                displayName = "Test Model",
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
        override suspend fun execute(request: AiRequest, context: AiContext) = provider.generate(request, context)
        override fun start() {}
        override fun stop() {}
    }

    @Test
    fun `tool definitions supplied to context only if provider supports tools and policy enabled`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val provider = ToolCallingProvider(supportsToolsCapability = true)
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()
        val registry = ToolRegistry(listOf(EchoToolExecutor()))

        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, blackboard, registry)
        val authorizer = InMemoryToolAuthorizer(registry)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            toolRegistry = registry,
            toolAuthorizer = authorizer,
            scope = this
        )

        // Request with includeAvailableTools = true
        val enabledPolicy = AiContextPolicy(includeAvailableTools = true)
        session.submit(AiRequest(text = "Hello", toolUseMode = ToolUseMode.OPTIONAL), policy = enabledPolicy)
        advanceUntilIdle()

        assertEquals(1, provider.capturedContexts.size)
        val ctx1 = provider.capturedContexts[0]
        assertEquals(1, ctx1.availableTools.size)
        assertEquals("halo.echo", ctx1.availableTools[0].id)

        // Request with includeAvailableTools = false
        val disabledPolicy = AiContextPolicy(includeAvailableTools = false)
        session.submit(AiRequest(text = "Hello 2", toolUseMode = ToolUseMode.OPTIONAL), policy = disabledPolicy)
        advanceUntilIdle()

        val ctx2 = provider.capturedContexts[1]
        assertTrue(ctx2.availableTools.isEmpty())
    }


    @Test
    fun `configured default policy is used when submit omits policy`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val provider = ToolCallingProvider(supportsToolsCapability = true)
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()
        val registry = ToolRegistry(listOf(EchoToolExecutor()))
        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(
            store,
            blackboard,
            registry
        )
        val authorizer = InMemoryToolAuthorizer(registry)

        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            toolRegistry = registry,
            toolAuthorizer = authorizer,
            scope = this,
            defaultPolicyProvider = {
                AiContextPolicy(includeAvailableTools = false)
            }
        )

        session.submit(AiRequest(text = "Hello"))
        advanceUntilIdle()

        assertEquals(1, provider.capturedContexts.size)
        assertTrue(provider.capturedContexts.single().availableTools.isEmpty())
    }

    @Test
    fun `provider without TOOLS capability receives no tool definitions`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val provider = ToolCallingProvider(supportsToolsCapability = false)
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()
        val registry = ToolRegistry(listOf(EchoToolExecutor()))

        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, blackboard, registry)
        val authorizer = InMemoryToolAuthorizer(registry)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            toolRegistry = registry,
            toolAuthorizer = authorizer,
            scope = this
        )

        val policy = AiContextPolicy(includeAvailableTools = true)
        session.submit(AiRequest(text = "Hello"), policy = policy)
        advanceUntilIdle()

        val ctx = provider.capturedContexts[0]
        assertTrue(ctx.availableTools.isEmpty())
    }

    @Test
    fun `successful tool call executes and completes tool loop`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val toolCall = ToolCall(id = "tc1", functionName = "halo.echo", arguments = mapOf("text" to "Hello World"))
        val provider = ToolCallingProvider(initialToolCalls = listOf(toolCall), finalResponseText = "Echoed: Hello World")
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()
        val registry = ToolRegistry(listOf(EchoToolExecutor()))

        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, blackboard, registry)
        val authorizer = InMemoryToolAuthorizer(registry)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            toolRegistry = registry,
            toolAuthorizer = authorizer,
            scope = this
        )

        val policy = AiContextPolicy(includeAvailableTools = true)
        session.submit(AiRequest(text = "Echo Hello World", toolUseMode = ToolUseMode.OPTIONAL), policy = policy)
        advanceUntilIdle()

        // Verify tool lifecycle events
        assertTrue(eventBus.published.any { it is ToolCallRequested })
        assertTrue(eventBus.published.any { it is ToolCallAuthorized })
        assertTrue(eventBus.published.any { it is ToolCallCompleted && it.success })

        // Verify conversation history appended turn
        assertEquals(1, store.turns.value.size)
        assertEquals("Echoed: Hello World", store.turns.value[0].assistantText)
    }

    @Test
    fun `unknown tool call is rejected during tool loop`() = runTest {
        val eventBus = FakeEventBus()
        val blackboard = DefaultBlackboard()
        val toolCall = ToolCall(id = "tc1", functionName = "unknown.tool", arguments = emptyMap())
        val provider = ToolCallingProvider(initialToolCalls = listOf(toolCall), finalResponseText = "Handled rejection.")
        val router = FakeRouter(provider)
        val store = InMemoryConversationStore()
        val registry = ToolRegistry(listOf(EchoToolExecutor()))

        val assembler = com.halo.runtime.cognition.context.DefaultContextAssembler(store, blackboard, registry)
        val authorizer = InMemoryToolAuthorizer(registry)
        val session = CognitiveSession(
            eventBus = eventBus,
            providerRouter = router,
            conversationStore = store,
            contextAssembler = assembler,
            toolRegistry = registry,
            toolAuthorizer = authorizer,
            scope = this
        )

        val policy = AiContextPolicy(includeAvailableTools = true)
        session.submit(AiRequest(text = "Run unknown tool", toolUseMode = ToolUseMode.OPTIONAL), policy = policy)
        advanceUntilIdle()

        // Verify ToolCallRejected event
        val rejectedEvent = eventBus.published.filterIsInstance<ToolCallRejected>().firstOrNull()
        assertNotNull(rejectedEvent)
        assertEquals("unknown.tool", rejectedEvent?.toolId)
    }
}
