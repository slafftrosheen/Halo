package com.halo.providers.android.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiHandoffProvider
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProvider
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Android assistant bridge.
 *
 * The system assistant is an external actor. Halo can detect it and launch it,
 * but does not treat it as an inference backend with a guaranteed response path.
 */
class AndroidAssistantProvider(
    private val context: Context
) : AiProvider, AiHandoffProvider {

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

    override fun isAvailable(): Boolean = resolveAssistantPackage() != null

    override fun assistantPackageName(): String? = resolveAssistantPackage()

    override fun handoff(prompt: String?) {
        context.startActivity(buildAssistIntent(prompt))
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

    /**
     * Handoff providers are intentionally not inference providers.
     * The method only satisfies the legacy AiProvider contract while the router
     * excludes this provider from normal inference resolution.
     */
    override suspend fun getCapabilities(): Set<AiCapability> = emptySet()

    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
        throw AiProviderException(
            AiErrorType.UNSUPPORTED_CAPABILITY,
            "Android Assistant is a handoff target, not a normal inference backend"
        )
    }

    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()

    override suspend fun listModels(): List<AiModel> = emptyList()

    override suspend fun testConnection(): Boolean = isAvailable()

    private fun buildAssistIntent(prompt: String?): Intent = Intent(Intent.ACTION_ASSIST).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
        putExtra(Intent.EXTRA_ASSIST_CONTEXT, Bundle())
        prompt?.trim()?.takeIf { it.isNotBlank() }?.let {
            // Non-standard OEM integration hook; Android does not guarantee this contract.
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
