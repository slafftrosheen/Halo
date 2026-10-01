package com.halo.runtime.cognition.turn

import com.halo.cognition.language.InteractionId
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiExecutionTarget
import com.halo.sdk.provider.AiFailureDetails
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.Recoverability
import com.halo.sdk.provider.ToolCall
import com.halo.sdk.provider.ToolDefinition
import com.halo.sdk.provider.ToolUseMode
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

    @Test
    fun `direct response retries once on RETRY_IMMEDIATELY without partial output and succeeds`() = runTest {
        val eventBus = FakeExecutorEventBus()
        val retryableError = AiProviderException(
            type = AiErrorType.PROVIDER_UNAVAILABLE,
            message = "503 Service Unavailable",
            details = AiFailureDetails(
                httpStatus = 503,
                recoverability = Recoverability.RETRY_IMMEDIATELY
            )
        )
        val provider = AttemptCountingProvider(listOf(
            { _ -> emptyList<AiStreamEvent>() to retryableError },
            { _ -> listOf(
                AiStreamEvent.Started,
                AiStreamEvent.Token("Retry success"),
                AiStreamEvent.Completed(AiResponse(text = "Retry success", providerId = "counting.provider"))
            ) to null }
        ))
        val executor = DefaultProviderTurnExecutor(eventBus)
        val target = createTarget(provider)

        val result = executor.execute(
            target = target,
            request = AiRequest(text = "hi", toolUseMode = ToolUseMode.NONE),
            context = AiContext.EMPTY,
            requestId = "req-retry-1",
            interactionId = InteractionId("interaction-retry-1")
        )

        assertEquals(2, provider.attemptCount)
        assertTrue(result is ProviderTurnResult.Response)
        assertEquals("Retry success", (result as ProviderTurnResult.Response).response.text)
    }

    @Test
    fun `direct response does not retry on AUTHENTICATION_FAILED`() = runTest {
        val eventBus = FakeExecutorEventBus()
        val authError = AiProviderException(
            type = AiErrorType.AUTHENTICATION_FAILED,
            message = "401 Unauthorized",
            details = AiFailureDetails(
                httpStatus = 401,
                recoverability = Recoverability.USER_ACTION_REQUIRED
            )
        )
        val provider = AttemptCountingProvider(listOf(
            { _ -> emptyList<AiStreamEvent>() to authError }
        ))
        val executor = DefaultProviderTurnExecutor(eventBus)
        val target = createTarget(provider)

        var thrown = false
        try {
            executor.execute(
                target = target,
                request = AiRequest(text = "hi", toolUseMode = ToolUseMode.NONE),
                context = AiContext.EMPTY,
                requestId = "req-auth-1",
                interactionId = InteractionId("interaction-auth-1")
            )
        } catch (e: AiProviderException) {
            thrown = true
            assertEquals(AiErrorType.AUTHENTICATION_FAILED, e.type)
        }

        assertTrue(thrown)
        assertEquals(1, provider.attemptCount)
    }

    @Test
    fun `direct response does not retry if token was emitted before error`() = runTest {
        val eventBus = FakeExecutorEventBus()
        val streamError = AiProviderException(
            type = AiErrorType.PROVIDER_UNAVAILABLE,
            message = "Dropped mid-stream",
            details = AiFailureDetails(
                recoverability = Recoverability.RETRY_IMMEDIATELY
            )
        )
        val provider = AttemptCountingProvider(listOf(
            { _ -> listOf(
                AiStreamEvent.Started,
                AiStreamEvent.Token("Partial output...")
            ) to streamError }
        ))
        val executor = DefaultProviderTurnExecutor(eventBus)
        val target = createTarget(provider)

        var thrown = false
        try {
            executor.execute(
                target = target,
                request = AiRequest(text = "hi", toolUseMode = ToolUseMode.NONE),
                context = AiContext.EMPTY,
                requestId = "req-partial-1",
                interactionId = InteractionId("interaction-partial-1")
            )
        } catch (e: Exception) {
            thrown = true
        }

        assertTrue(thrown)
        assertEquals(1, provider.attemptCount)
    }

    @Test
    fun `agentic required mode does not retry same target on failure`() = runTest {
        val eventBus = FakeExecutorEventBus()
        val retryableError = AiProviderException(
            type = AiErrorType.PROVIDER_UNAVAILABLE,
            message = "503 Service Unavailable",
            details = AiFailureDetails(
                httpStatus = 503,
                recoverability = Recoverability.RETRY_IMMEDIATELY
            )
        )
        val provider = AttemptCountingProvider(listOf(
            { _ -> emptyList<AiStreamEvent>() to retryableError }
        ))
        val executor = DefaultProviderTurnExecutor(eventBus)
        val target = createTarget(provider)

        var thrown = false
        try {
            executor.execute(
                target = target,
                request = AiRequest(text = "do action", toolUseMode = ToolUseMode.REQUIRED),
                context = AiContext(availableTools = listOf(ToolDefinition("halo.echo", "halo.echo", "desc"))),
                requestId = "req-agentic-1",
                interactionId = InteractionId("interaction-agentic-1")
            )
        } catch (e: Exception) {
            thrown = true
        }

        assertTrue(thrown)
        assertEquals(1, provider.attemptCount)
    }
}

private class AttemptCountingProvider(
    private val attemptBehaviors: List<(Int) -> Pair<List<AiStreamEvent>, Throwable?>>
) : AiInferenceProvider {
    var attemptCount = 0
    override val id: String = "counting.provider"
    override val displayName: String = "Counting Provider"
    override val isLocal: Boolean = false
    override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)

    override suspend fun getStatus(): ProviderStatus = status.value
    override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.STREAMING)

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
        throw UnsupportedOperationException()

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        attemptCount++
        val behaviorIndex = (attemptCount - 1).coerceAtMost(attemptBehaviors.size - 1)
        val (events, error) = attemptBehaviors[behaviorIndex](attemptCount)
        for (e in events) {
            emit(e)
        }
        if (error != null) throw error
    }

    override suspend fun listModels(): List<AiModel> = emptyList()
}
