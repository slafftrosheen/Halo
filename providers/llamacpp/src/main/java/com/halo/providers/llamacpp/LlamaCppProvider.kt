package com.halo.providers.llamacpp

import com.halo.libraries.common.core.Identifier
import com.halo.sdk.provider.Provider
import com.halo.sdk.provider.ProviderRequest
import com.halo.sdk.provider.ProviderResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class LlamaCppProvider : Provider {
    override val id = Identifier("provider.llamacpp")

    override suspend fun chat(request: ProviderRequest): ProviderResponse {
        TODO("Not yet implemented")
    }

    override suspend fun vision(request: ProviderRequest): ProviderResponse {
        TODO("Not yet implemented")
    }

    override suspend fun embed(text: String): List<Float> {
        TODO("Not yet implemented")
    }

    override suspend fun stream(request: ProviderRequest): Flow<ProviderResponse> {
        return emptyFlow()
    }

    override suspend fun cancel() {
        // Implementation
    }

    override suspend fun health(): Boolean {
        return true
    }
}
