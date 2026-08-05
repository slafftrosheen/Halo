package com.halo.libraries.testing

import com.halo.sdk.provider.*

class FakeProvider(
    private val providerId: String
) : Provider {
    override suspend fun complete(request: AIRequest): AIResponse {
        return AIResponse(
            text = "Mock provider response from $providerId",
            finishReason = "stop",
            latencyMs = 100
        )
    }
}
