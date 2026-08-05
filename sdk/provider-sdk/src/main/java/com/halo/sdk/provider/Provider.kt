package com.halo.sdk.provider

import com.halo.libraries.common.core.Identifier
import kotlinx.coroutines.flow.Flow

interface ProviderRequest
interface ProviderResponse

interface Provider {
    val id: Identifier
    suspend fun chat(request: ProviderRequest): ProviderResponse
    suspend fun vision(request: ProviderRequest): ProviderResponse
    suspend fun embed(text: String): List<Float>
    suspend fun stream(request: ProviderRequest): Flow<ProviderResponse>
    suspend fun cancel()
    suspend fun health(): Boolean
}
