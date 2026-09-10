package com.halo.providers.android.assistant

import android.content.Context
import com.halo.sdk.provider.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Bridge to the Android device's configured assistant.
 *
 * Android does not expose a general public API for injecting arbitrary text into
 * another assistant app and receiving its generated response. Halo therefore
 * models this provider as a handoff target rather than pretending it is an
 * inference backend.
 */
class AndroidAssistantProvider(
    private val context: Context
) : AiProvider {

    override val id: String = "android.system.assistant"
    override val displayName: String = "Phone Assistant"
    override val isLocal: Boolean = false

    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    private val handoffModel = AiModel(
        id = "android-assistant/handoff",
        displayName = "System Assistant Handoff",
        providerId = id,
        inputCapabilities = setOf(AiCapability.TEXT),
        contextLength = null,
        supportsStreaming = false,
        supportsTools = false
    )

    override suspend fun getStatus(): ProviderStatus {
        val intent = android.content.Intent(android.content.Intent.ACTION_ASSIST)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        val current = if (resolveInfo != null) {
            ProviderStatus(ProviderStatus.State.READY, "System assistant available for handoff")
        } else {
            ProviderStatus(ProviderStatus.State.UNAVAILABLE, "No system assistant app found")
        }
        _status.value = current
        return current
    }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT)

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        request.validateForTextGeneration()
        val intent = android.content.Intent(android.content.Intent.ACTION_ASSIST).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(android.content.Intent.EXTRA_ASSIST_CONTEXT, android.os.Bundle())
        }
        this.context.startActivity(intent)
        return AiResponse(
            text = "System assistant opened. Android does not expose a public API for injecting Halo's text into the assistant or receiving its response.",
            providerId = id,
            modelId = handoffModel.id,
            finishReason = "handoff"
        )
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()

    override suspend fun listModels(): List<AiModel> = listOf(handoffModel)
}
