package com.halo.providers.llamacpp

import com.halo.libraries.common.core.Identifier
import com.halo.sdk.provider.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class LlamaCppProvider : Provider {
    val id = Identifier("provider.llamacpp")
    
    override suspend fun complete(request: AIRequest): AIResponse {
        return AIResponse("", "stop", 0)
    }

    suspend fun stream(request: AIRequest): Flow<AIResponse> {
        return emptyFlow()
    }

    suspend fun cancel() {
        // Implementation
    }

    suspend fun health(): Boolean {
        return true
    }
}
