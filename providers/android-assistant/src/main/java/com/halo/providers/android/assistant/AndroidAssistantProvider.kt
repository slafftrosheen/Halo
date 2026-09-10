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
 * This is intentionally a handoff provider. Android exposes the system assistant
 * as a role and through ACTION_ASSIST, but third-party apps do not get a generic
 * public API for sending arbitrary prompt text to Gemini/ChatGPT and receiving a
 * generated answer back.
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
        val intent = Intent(Intent.ACTION_ASSIST)
        val resolveInfo = context.packageManager.resolveActivity(intent, 0)
        val roleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            false
        }

        val current = when {
            roleHeld -> ProviderStatus(ProviderStatus.State.READY, "Default Android assistant available")
            resolveInfo != null -> ProviderStatus(ProviderStatus.State.READY, "Android assistant handoff available")
            else -> ProviderStatus(ProviderStatus.State.UNAVAILABLE, "No Android assistant is available")
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

        val intent = Intent(Intent.ACTION_ASSIST).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(Intent.EXTRA_ASSIST_CONTEXT, Bundle())
            putExtra("com.halo.assistant.PROMPT", prompt)
        }

        val resolveInfo = this.context.packageManager.resolveActivity(intent, 0)
            ?: throw AiProviderException(
                AiErrorType.MODEL_UNAVAILABLE,
                "No Android assistant activity can handle ACTION_ASSIST"
            )

        this.context.startActivity(intent)
        return AiResponse(
            text = "Assistant handoff launched (${resolveInfo.activityInfo.packageName}). The Android public API does not provide Halo with the assistant's generated response.",
            providerId = id,
            modelId = handoffModel.id,
            finishReason = "handoff"
        )
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()

    override suspend fun listModels(): List<AiModel> = listOf(handoffModel)
}
