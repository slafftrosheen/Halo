package com.halo.providers.android.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.halo.sdk.provider.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Android assistant bridge.
 *
 * This provider intentionally represents the device assistant as a handoff target.
 * Android exposes assistant selection through ROLE_ASSISTANT and launching through
 * ACTION_ASSIST, but does not expose a generic public API for Halo to send arbitrary
 * prompt text to a third-party assistant and capture its generated answer.
 */
class AndroidAssistantProvider(
    private val context: Context
) : AiProvider {

    override val id: String = "android.system.assistant"
    override val displayName: String = "Android Assistant"
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
        val assistIntent = Intent(Intent.ACTION_ASSIST)
        val resolveInfo = context.packageManager.resolveActivity(assistIntent, 0)
        val roleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            false
        }

        val current = when {
            roleHeld && resolveInfo != null -> ProviderStatus(
                ProviderStatus.State.READY,
                "Default Android assistant available: ${resolveInfo.activityInfo.packageName}"
            )
            resolveInfo != null -> ProviderStatus(
                ProviderStatus.State.READY,
                "Android assistant handoff available: ${resolveInfo.activityInfo.packageName}"
            )
            else -> ProviderStatus(
                ProviderStatus.State.UNAVAILABLE,
                "No Android assistant activity can handle ACTION_ASSIST"
            )
        }
        _status.value = current
        return current
    }

    override suspend fun getCapabilities(): Set<AiCapability> = setOf(AiCapability.TEXT)

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        request.validateForTextGeneration()
        val prompt = request.text?.trim().orEmpty()
        if (prompt.isBlank()) {
            throw AiProviderException(
                AiErrorType.INVALID_REQUEST,
                "Assistant handoff requires non-empty text"
            )
        }

        val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_ASSIST_CONTEXT, Bundle())

            // This extra is deliberately namespaced by Halo. Android does not
            // define it as a standard prompt-injection contract for assistants.
            // It is useful to OEM/assistant integrations that explicitly opt in,
            // while the provider remains truthful about the generic API boundary.
            putExtra("com.halo.assistant.PROMPT", prompt)
        }

        val resolveInfo = this.context.packageManager.resolveActivity(assistIntent, 0)
            ?: throw AiProviderException(
                AiErrorType.MODEL_UNAVAILABLE,
                "No Android assistant activity can handle ACTION_ASSIST"
            )

        this.context.startActivity(assistIntent)
        return AiResponse(
            text = "Assistant handoff launched (${resolveInfo.activityInfo.packageName}). No generic public Android API returns the assistant's generated response to Halo.",
            providerId = id,
            modelId = handoffModel.id,
            finishReason = "handoff"
        )
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()

    override suspend fun listModels(): List<AiModel> = listOf(handoffModel)
}
