package com.halo.providers.cerebras

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Cerebras preset over the shared OpenAI-compatible fabric.
 *
 * Auth: API key
 * Access: free tier available
 * Protocol: OpenAI chat completions where supported.
 */
class CerebrasProvider(
    private val storage: Storage,
    private val transport: OpenAiCompatibleTransport = OpenAiCompatibleTransport.defaultClient().let {
        OpenAiCompatibleTransport(it, "https://api.cerebras.ai/v1", AiWireProtocol.OPENAI_CHAT_COMPLETIONS)
    },
    private val credentialProvider: CredentialProvider = CerebrasCredentialProvider(storage),
) : AiInferenceProvider, CredentialProvider by credentialProvider, ProviderConfigurationReporting {

    override val id: String = RemoteProviderIds.CEREBRAS
    override val displayName: String = "Cerebras"
    override val isLocal: Boolean = false

    private val descriptor = RemoteProviderPresets.cerebras
    private val configured: ConfiguredOpenAiCompatibleProvider = ConfiguredOpenAiCompatibleProvider(
        descriptor = descriptor,
        transport = transport,
        credentialProvider = credentialProvider,
        systemPrompt = storage.getString("cerebras_system_prompt") ?: "You are Halo, a helpful AI assistant.",
        allowInsecureLocalCredentialTransport = false,
    )

    override val configurationDescriptor: ProviderConfigurationDescriptor get() = configured.configurationDescriptor

    override val status: StateFlow<ProviderStatus> get() = configured.status
    val authInfo: StateFlow<ProviderAuthInfo> get() = configured.authInfo

    override suspend fun getCapabilities(): Set<AiCapability> = configured.getCapabilities()
    override fun configurationChanged() = configured.configurationChanged()
    override suspend fun getStatus(): ProviderStatus = configured.getStatus()
    override suspend fun testConnection(): Boolean = configured.testConnection()
    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = configured.generate(request, context)
    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = configured.stream(request, context)
    override suspend fun listModels(): List<AiModel> = configured.listModels()
}

class CerebrasCredentialProvider(
    private val storage: Storage,
) : CredentialProvider {
    private val keyStorage = "cerebras_api_key"

    override fun hasCredential(): Boolean = storage.getString(keyStorage)?.takeIf { it.isNotBlank() } != null
    override fun credential(): Credential? = storage.getString(keyStorage)?.trim()?.ifBlank { null }?.let {
        Credential(id = "cerebras", value = it, label = "Cerebras API key")
    }
    override fun setCredential(credential: Credential) { storage.putString(keyStorage, credential.value) }
    override fun clearCredential() { storage.remove(keyStorage) }
}
