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
 * Halo treats the device assistant as an external cognitive actor. Android exposes
 * assistant selection and ACTION_ASSIST launching, but does not expose a generic
 * public API for sending arbitrary prompt text to a third-party assistant and
 * reading its generated response back into Halo.
 */
class AndroidAssistantProvider(
    private val context: Context
) : AiProvider {

    companion object {
        const val HANDOFF_ACTION = "com.halo.assistant.HANDOFF"
    }

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

    /** True when a concrete ACTION_ASSIST target exists. */
    fun isAvailable(): Boolean = resolveAssistantPackage() != null

    /** Returns the resolved assistant package, when Android exposes one. */
    fun assistantPackageName(): String? = resolveAssistantPackage()

    /**
     * Launches the configured Android assistant. The prompt is supplied only as a
     * best-effort, non-standard extra; callers must not expect a response callback.
     */
    fun handoff(prompt: String? = null) {
        val assistIntent = buildAssistIntent(prompt)
        context.startActivity(assistIntent)
    }

    override suspend fun getStatus(): ProviderStatus {
        val packageName = resolveAssistantPackage()
        val roleHeld = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
        } else {
            false
        }

        val current = when {
            packageName == null -> ProviderStatus(
                ProviderStatus.State.UNAVAILABLE,
                "No Android assistant activity can handle ACTION_ASSIST"
            )
            roleHeld -> ProviderStatus(
                ProviderStatus.State.READY,
                "Default Android assistant: $packageName"
            )
            else -> ProviderStatus(
                ProviderStatus.State.READY,
                "Android assistant handoff available: $packageName"
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

        val packageName = resolveAssistantPackage()
            ?: throw AiProviderException(
                AiErrorType.MODEL_UNAVAILABLE,
                "No Android assistant activity can handle ACTION_ASSIST"
            )

        handoff(prompt)
        return AiResponse(
            text = "Assistant handoff launched ($packageName). Android does not provide a generic public response channel for Halo.",
            providerId = id,
            modelId = handoffModel.id,
            finishReason = "handoff"
        )
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()

    override suspend fun listModels(): List<AiModel> = listOf(handoffModel)

    private fun buildAssistIntent(prompt: String?): Intent = Intent(Intent.ACTION_ASSIST).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        putExtra(Intent.EXTRA_ASSIST_CONTEXT, Bundle())
        prompt?.trim()?.takeIf { it.isNotBlank() }?.let {
            // Not an Android-standard assistant prompt contract. This is a best-effort
            // OEM integration hook and must never be treated as guaranteed behavior.
            putExtra("com.halo.assistant.PROMPT", it)
        }
        putExtra("com.halo.assistant.ACTION", HANDOFF_ACTION)
    }

    private fun resolveAssistantPackage(): String? {
        val resolveInfo = context.packageManager.resolveActivity(Intent(Intent.ACTION_ASSIST), 0)
            ?: return null
        return resolveInfo.activityInfo?.packageName
    }
}
