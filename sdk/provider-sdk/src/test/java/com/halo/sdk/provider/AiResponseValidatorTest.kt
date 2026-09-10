package com.halo.sdk.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AiResponseValidatorTest {

    private class MockProvider(
        override val id: String = "p1",
        override val displayName: String = "P1",
        override val isLocal: Boolean = true
    ) : AiProvider {
        override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY)
        override suspend fun getStatus() = status.value
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT)
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            return AiResponse("OK", providerId = id)
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        override suspend fun listModels() = emptyList<AiModel>()
    }

    @Test
    fun `matching provider ID and text is valid`() {
        val provider = MockProvider("p1")
        val response = AiResponse("Hello", providerId = "p1")
        AiResponseValidator.validate(provider, response)
    }

    @Test
    fun `provider identity mismatch throws INVALID_RESPONSE`() {
        val provider = MockProvider("p1")
        val response = AiResponse("Hello", providerId = "p2")
        try {
            AiResponseValidator.validate(provider, response)
            fail("Expected AiProviderException")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.INVALID_RESPONSE, e.type)
        }
    }

    @Test
    fun `empty text and empty toolCalls throws EMPTY_RESPONSE`() {
        val provider = MockProvider("p1")
        val response = AiResponse("", toolCalls = emptyList(), providerId = "p1")
        try {
            AiResponseValidator.validate(provider, response)
            fail("Expected AiProviderException")
        } catch (e: AiProviderException) {
            assertEquals(AiErrorType.EMPTY_RESPONSE, e.type)
        }
    }

    @Test
    fun `tool call only response is valid`() {
        val provider = MockProvider("p1")
        val toolCall = ToolCall("tc1", "halo.echo", mapOf("text" to "hi"))
        val response = AiResponse("", toolCalls = listOf(toolCall), providerId = "p1")
        AiResponseValidator.validate(provider, response)
    }
}
