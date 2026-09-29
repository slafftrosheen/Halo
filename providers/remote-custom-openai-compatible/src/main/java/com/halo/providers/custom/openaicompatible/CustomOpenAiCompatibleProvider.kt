package com.halo.providers.custom.openaicompatible

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Custom OpenAI-compatible preset.
 *
 * This unlocks vLLM, LM Studio, llama.cpp server, private gateways and future
 * providers without writing another Halo module.
 *
 * Local/no-auth endpoints are permitted for explicitly local/private transport.
 * Non-loopback no-auth servers require explicit user confirmation.
 */
class CustomOpenAiCompatibleProvider(
    private val storage: Storage,
    private val transport: OpenAiCompatibleTransport,
    private val credentialProvider: CredentialProvider,
) : AiInferenceProvider, CredentialProvider by credentialProvider {

    override val id: String = RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE
    override val displayName: String get() = "Custom OpenAI-compatible"
    override val isLocal: Boolean = true

    private val descriptor = RemoteProviderPresets.customOpenAiCompatible
    private val configured: ConfiguredOpenAiCompatibleProvider = ConfiguredOpenAiCompatibleProvider(
        descriptor = descriptor,
        transport = transport,
        credentialProvider = credentialProvider,
        systemPrompt = storage.getString("custom_openai_compatible_system_prompt") ?: "You are Halo, a helpful AI assistant.",
        allowInsecureLocalCredentialTransport = true,
        fallbackWhenUnauthenticated = true,
        staticHints = emptyList(),
    )

    override val status: StateFlow<ProviderStatus> get() = configured.status
    val authInfo: StateFlow<ProviderAuthInfo> get() = configured.authInfo

    override suspend fun getCapabilities(): Set<AiCapability> = configured.getCapabilities()
    override suspend fun getStatus(): ProviderStatus = configured.getStatus()
    override suspend fun testConnection(): Boolean = configured.testConnection()
    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = configured.generate(request, context)
    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = configured.stream(request, context)
    override suspend fun listModels(): List<AiModel> = configured.listModels()
}

class CustomOpenAiCompatibleCredentialProvider(
    private val storage: Storage,
    private val mode: CustomAuthMode = CustomAuthMode.NONE,
) : CredentialProvider {
    private val keyStorage = "custom_openai_compatible_credential"

    override fun hasCredential(): Boolean = when (mode) {
        CustomAuthMode.NONE -> false
        CustomAuthMode.API_KEY, CustomAuthMode.BEARER_TOKEN -> storage.getString(keyStorage)?.takeIf { it.isNotBlank() } != null
    }

    override fun credential(): Credential? = when (mode) {
        CustomAuthMode.NONE -> null
        CustomAuthMode.API_KEY -> storage.getString(keyStorage)?.trim()?.ifBlank { null }?.let {
            Credential(id = "custom.api_key", value = it, label = "Custom API key")
        }
        CustomAuthMode.BEARER_TOKEN -> storage.getString(keyStorage)?.trim()?.ifBlank { null }?.let {
            Credential(id = "custom.bearer", value = it, label = "Custom bearer token")
        }
    }

    override fun setCredential(credential: Credential) { storage.putString(keyStorage, credential.value) }
    override fun clearCredential() { storage.remove(keyStorage) }
}

enum class CustomAuthMode {
    NONE,
    API_KEY,
    BEARER_TOKEN,
}
