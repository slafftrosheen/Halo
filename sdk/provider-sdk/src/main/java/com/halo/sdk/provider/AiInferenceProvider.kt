package com.halo.sdk.provider

import kotlinx.coroutines.flow.Flow

/**
 * Contract for providers that perform actual model inference.
 *
 * External assistant handoff is intentionally separate and is represented by
 * [AiHandoffProvider].
 */
interface AiInferenceProvider : AiProvider {
    override suspend fun getCapabilities(): Set<AiCapability>
    suspend fun generate(request: AiRequest, context: AiContext): AiResponse
    fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent>
    suspend fun listModels(): List<AiModel>
}
