package com.halo.providers.android.assistant

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiHandoffProvider
import com.halo.sdk.provider.ProviderStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Android assistant bridge.
 *
 * The system assistant is an external actor. Halo can detect it and launch it,
 * but does not treat it as an inference backend with a model/response contract.
 */
class AndroidAssistantProvider(
    private val context: Context
) : AiHandoffProvider {

    companion object {
        const val ID = "android.system.assistant"
        const val DISPLAY_NAME = "Android Assistant"
        const val HANDOFF_ACTION = "com.halo.assistant.HANDOFF"
    }

    override val id: String = ID
    override val displayName: String = DISPLAY_NAME
    override val isLocal: Boolean = true

    override suspend fun getCapabilities(): Set<AiCapability> = emptySet()

    @Volatile
    private var cachedAssistantPackageName: String? = null

    override fun isAvailable(): Boolean = cachedAssistantPackageName != null

    override fun assistantPackageName(): String? = cachedAssistantPackageName

    override fun handoff(prompt: String?) {
        context.startActivity(buildAssistIntent(prompt))
    }

    private val _status = MutableStateFlow(ProviderStatus.UNAVAILABLE)
    override val status: StateFlow<ProviderStatus> = _status.asStateFlow()

    override suspend fun getStatus(): ProviderStatus = withContext(Dispatchers.IO) {
        val packageName = resolveAssistantPackage()
        cachedAssistantPackageName = packageName

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
        current
    }

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
