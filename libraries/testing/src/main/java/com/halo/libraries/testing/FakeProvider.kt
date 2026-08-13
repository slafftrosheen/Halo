package com.halo.libraries.testing

import com.halo.sdk.provider.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class FakeProvider(
    private val providerId: String
) : InferenceProvider {
    override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY).asStateFlow()
    
    override suspend fun health(): Boolean = true

    override suspend fun complete(request: AIRequestLegacy): AIResponseLegacy {
        return AIResponseLegacy(
            text = "Mock provider response from $providerId",
            finishReason = "stop",
            latencyMs = 100
        )
    }

    override fun stream(prompt: String): Flow<String> = flow {
        emit("Mock provider stream from $providerId")
    }

    override suspend fun listModels(): List<AiModel> = emptyList()
}
