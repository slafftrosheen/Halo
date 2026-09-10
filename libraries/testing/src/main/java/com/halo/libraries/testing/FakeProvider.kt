package com.halo.libraries.testing

import com.halo.sdk.provider.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow

class FakeProvider(
    private val providerId: String
) : AiInferenceProvider {
    override val status: StateFlow<ProviderStatus> = MutableStateFlow(ProviderStatus.READY).asStateFlow()
    override val id: String = "provider.test.$providerId"
    override val displayName: String = "Test provider $providerId"
    override val isLocal: Boolean = true

    override suspend fun getCapabilities(): Set<AiCapability> =
        setOf(AiCapability.TEXT, AiCapability.STREAMING)

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
        AiResponse(
            text = "Mock provider response from $providerId",
            providerId = id,
            modelId = request.modelId
        )

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        emit(AiStreamEvent.Started)
        emit(AiStreamEvent.Token("Mock provider stream from $providerId"))
        emit(
            AiStreamEvent.Completed(
                AiResponse(
                    text = "Mock provider stream from $providerId",
                    providerId = id,
                    modelId = request.modelId
                )
            )
        )
    }

    override suspend fun listModels(): List<AiModel> = emptyList()
}
