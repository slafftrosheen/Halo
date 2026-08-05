package com.halo.libraries.testing

import com.halo.libraries.common.core.Identifier
import com.halo.sdk.provider.Provider
import com.halo.sdk.provider.ProviderRequest
import com.halo.sdk.provider.ProviderResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

class FakeProvider(id: String) : Provider {
    override val id = Identifier(id)

    override suspend fun chat(request: ProviderRequest): ProviderResponse {
        return object : ProviderResponse {}
    }

    override suspend fun vision(request: ProviderRequest): ProviderResponse {
        return object : ProviderResponse {}
    }

    override suspend fun embed(text: String): List<Float> {
        return emptyList()
    }

    override suspend fun stream(request: ProviderRequest): Flow<ProviderResponse> {
        return emptyFlow()
    }

    override suspend fun cancel() {
    }

    override suspend fun health(): Boolean {
        return true
    }
}
