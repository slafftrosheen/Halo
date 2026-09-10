package com.halo.sdk.provider

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Contract for providers that perform actual model inference.
 *
 * Kept separate from AiProvider so external assistant handoff does not need to
 * implement fake generation/model APIs.
 */
interface AiInferenceProvider : AiProvider {
    override suspend fun getCapabilities(): Set<AiCapability>
    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse
    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent>
    override suspend fun listModels(): List<AiModel>
}
