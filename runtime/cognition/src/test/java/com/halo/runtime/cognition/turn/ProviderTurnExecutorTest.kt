package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiExecutionTarget
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.HaloEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakeExecutorEventBus : EventBus {
    val published = mutableListOf<HaloEvent>()
    override suspend fun publish(event: HaloEvent) { published.add(event) }
    override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = flow {}
    override fun events(): Flow<HaloEvent> = flow {}
}

private class MockStreamProvider(
    private val eventsToEmit: List<AiStreamEvent>,
    private val shouldThrow: Throwable? = null,
) : AiInferenceProvider {
    override val id: String = "mock.provider"
    override val displayName: String = "Mock Provider"
    override val isLocal: Boolean = true
    override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)

    override suspend fun getStatus(): ProviderStatus = status.value
    override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.STREAMING)

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        val completed = eventsToEmit.filterIsInstance<AiStreamEvent.Completed>().firstOrNull()
        return completed?.response ?: AiResponse(text = "default response", providerId = id)
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        if (shouldThrow != null) throw shouldThrow
        for (event in eventsToEmit) {
            emit(event)
        }
    }

    override suspend fun listModels(): List<AiModel> = emptyList()
}

class ProviderTurnExecutorTest {

    private fun createTarget(provider: AiInferenceProvider, capabilities: Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.STREAMING, AiCapability.TOOLS)): AiExecutionTarget {
        val model = AiModel(
            id = "mock-model",
            displayName = "Mock Model",
            providerId = provider.id,
            inputCapabilities = setOf(AiCapability.TEXT),
            contextLength = 4096,
            supportsStreaming = true,
            supportsTools = true
        )
        return AiExecutionTarget(provider = provider, model = model, providerCapabilities = capabilities, effectiveCapabilities = capabilities)
    }

    @Test
    fun `streaming turn emits tokens and completes successfully`() = runTest {
        val eventBus = FakeExecutorEventBus()
        val provider = MockStreamProvider(
            eventsToEmit = listOf(
                AiStreamEvent.Started,
                AiStreamEvent.Token("Hello"),
                AiStreamEvent.Token(" World"),
                AiStreamEvent.Completed(AiResponse(text = "Hello World", providerId = "mock.provider", finishReason = "stop"))
            )
        )
        val executor = DefaultProviderTurnExecutor(eventBus)
        val target = createTarget(provider)

        val result = executor.execute(
            target = target,
            request = AiRequest(text = "hi"),
            context = AiContext.EMPTY,
            requestId = "req-1",
            interactionId = InteractionId("interaction-1")
        )

        assertTrue(result is ProviderTurnResult.Response)
        val response = (result as ProviderTurnResult.Response).response
        assertEquals("Hello World", response.text)
        assertEquals("stop", response.finishReason)
    }

    @Test
    fun `streaming turn failing mid stream throws error`() = runTest {
        val eventBus = FakeExecutorEventBus()
        val provider = MockStreamProvider(
            eventsToEmit = emptyList(),
            shouldThrow = IOException("Connection dropped")
        )
        val executor = DefaultProviderTurnExecutor(eventBus)
        val target = createTarget(provider)

        var errorCount = 0
        try {
            executor.execute(
                target = target,
                request = AiRequest(text = "hi"),
                context = AiContext.EMPTY,
                requestId = "req-1",
                interactionId = InteractionId("interaction-1")
            )
        } catch (e: Exception) {
            errorCount++
            assertTrue(e is IOException)
        }
        assertEquals(1, errorCount)
    }

    @Test
    fun `cancellation propagates as cancellation`() = runTest {
        val eventBus = FakeExecutorEventBus()
        val provider = MockStreamProvider(
            eventsToEmit = emptyList(),
            shouldThrow = CancellationException("Turn cancelled")
        )
        val executor = DefaultProviderTurnExecutor(eventBus)
        val target = createTarget(provider)

        var cancelled = false
        try {
            executor.execute(
                target = target,
                request = AiRequest(text = "hi"),
                context = AiContext.EMPTY,
                requestId = "req-1",
                interactionId = InteractionId("interaction-1")
            )
        } catch (e: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
    }

    @Test
    fun `tool calls in completion response are returned in turn result`() = runTest {
        val eventBus = FakeExecutorEventBus()
        val toolCall = ToolCall(id = "call-1", functionName = "my_tool", arguments = mapOf("key" to "value"))
        val provider = MockStreamProvider(
            eventsToEmit = listOf(
                AiStreamEvent.Started,
                AiStreamEvent.Completed(AiResponse(text = "", toolCalls = listOf(toolCall), providerId = "mock.provider", finishReason = "tool_calls"))
            )
        )
        val executor = DefaultProviderTurnExecutor(eventBus)
        val target = createTarget(provider)

        val result = executor.execute(
            target = target,
            request = AiRequest(text = "hi"),
            context = AiContext.EMPTY,
            requestId = "req-1",
            interactionId = InteractionId("interaction-1")
        )

        assertTrue(result is ProviderTurnResult.Response)
        val response = (result as ProviderTurnResult.Response).response
        assertEquals(1, response.toolCalls.size)
        assertEquals("my_tool", response.toolCalls[0].functionName)
    }
}
