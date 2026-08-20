package com.halo.sdk.provider

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for routing AI requests to the appropriate provider.
 */
interface AiProviderRouter {
    val selectedProviderId: StateFlow<String?>
    val selectedModelId: StateFlow<String?>

    fun selectProvider(id: String)
    fun selectModel(modelId: String?)

    fun getAllProviders(): List<AiProvider>
    
    suspend fun getEligibleProvider(request: AiRequest): AiProvider
    
    suspend fun execute(
        request: AiRequest, 
        context: AiContext = AiContext.EMPTY
    ): AiResponse
    
    fun start()
    fun stop()
}
