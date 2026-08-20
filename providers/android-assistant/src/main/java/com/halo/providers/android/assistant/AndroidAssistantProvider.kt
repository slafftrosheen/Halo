package com.halo.providers.android.assistant

import android.content.Context
import com.halo.sdk.provider.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * AI Provider that integrates with the Android system assistant.
 *
 * This implementation acts as a bridge to the default assistant app configured on the device.
 */
class AndroidAssistantProvider(
    private val context: Context
) : AiProvider {

    override val id: String = "android.system.assistant"
    override val displayName: String = "Phone Assistant"
    override val isLocal: Boolean = false

    private val _status = MutableStateFlow(ProviderStatus.READY)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    override suspend fun getStatus(): ProviderStatus {
        val intent = android.content.Intent(android.content.Intent.ACTION_ASSIST)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        
        val current = if (resolveInfo != null) {
            ProviderStatus(ProviderStatus.State.UNAVAILABLE, "Inline cognitive reasoning not supported by OS assistant")
        } else {
            ProviderStatus(ProviderStatus.State.UNAVAILABLE, "No system assistant app found")
        }
        _status.value = current
        return current
    }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(
        AiCapability.TEXT,
        AiCapability.IMAGE,
        AiCapability.AUDIO,
        AiCapability.MULTIMODAL
    )

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        // Implementation note: Android's public Assistant integration is primarily
        // based on intents and assist context. 

        android.util.Log.i("HALO-AI", "provider=$id operation=generate capability=TEXT")

        // Launch assistant intent for handoff if text is provided
        if (request.text != null) {
            val intent = android.content.Intent(android.content.Intent.ACTION_ASSIST).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(android.content.Intent.EXTRA_ASSIST_CONTEXT, android.os.Bundle())
            }
            this.context.startActivity(intent)
            
            return AiResponse(
                text = "HANDOFF_STARTED",
                providerId = id
            )
        }

        return AiResponse(
            text = "UNSUPPORTED",
            providerId = id
        )
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()

    override suspend fun listModels(): List<AiModel> = emptyList()
}
