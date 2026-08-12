package com.halo.libraries.testing

import com.halo.sdk.provider.*

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FakeProvider(
    private val providerId: String
) : InferenceProvider {
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
}
