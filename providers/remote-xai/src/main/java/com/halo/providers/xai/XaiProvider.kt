package com.halo.providers.xai

import kotlinx.coroutines.flow.flowOn

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * xAI direct API preset over the shared OpenAI-compatible fabric.
 *
 * Auth: API key
 * Access: pay-as-you-go
 * Protocol: OpenAI compatible / Responses where the provider/model supports it.
 *
 * This is NOT labelled free. Consumer Grok Free is not the same as free xAI API.
 */
class XaiProvider(
    private val storage: Storage,
    private val transport: OpenAiCompatibleTransport = OpenAiCompatibleTransport.defaultClient().let {
        OpenAiCompatibleTransport(it, "https://api.x.ai/v1", AiWireProtocol.OPENAI_CHAT_COMPLETIONS)
    },
    private val credentialProvider: CredentialProvider = XaiCredentialProvider(storage),
) : AiInferenceProvider, CredentialProvider by credentialProvider, ProviderConfigurationReporting {

    override val id: String = RemoteProviderIds.XAI
    override val displayName: String = "xAI"
    override val isLocal: Boolean = false

    private val descriptor = RemoteProviderPresets.xai
    private val configured: ConfiguredOpenAiCompatibleProvider = ConfiguredOpenAiCompatibleProvider(
        descriptor = descriptor,
        transport = transport,
        credentialProvider = credentialProvider,
        systemPrompt = storage.getString("xai_system_prompt") ?: "You are Halo, a helpful AI assistant.",
        allowInsecureLocalCredentialTransport = false,
    )

    override val configurationDescriptor: ProviderConfigurationDescriptor get() = configured.configurationDescriptor

    override val status: StateFlow<ProviderStatus> get() = configured.status
    val authInfo: StateFlow<ProviderAuthInfo> get() = configured.authInfo

    override suspend fun getCapabilities(): Set<AiCapability> = configured.getCapabilities()
    override fun configurationChanged() = configured.configurationChanged()
    override suspend fun getStatus(): ProviderStatus = configured.getStatus()
    override suspend fun testConnection(): Boolean = configured.testConnection()
    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = kotlinx.coroutines.withContext(storage.remoteAiContext(id, request, context)) { configured.generate(request, context) }
    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = configured.stream(request, context).flowOn(storage.remoteAiContext(id, request, context))
    override suspend fun listModels(): List<AiModel> = configured.listModels()
}

class XaiCredentialProvider(
    private val storage: Storage,
) : CredentialProvider {
    private val keyStorage = "xai_api_key"

    override fun hasCredential(): Boolean = storage.getString(keyStorage)?.takeIf { it.isNotBlank() } != null
    override fun credential(): Credential? = storage.getString(keyStorage)?.trim()?.ifBlank { null }?.let {
        Credential(id = "xai", value = it, label = "xAI API key")
    }
    override fun setCredential(credential: Credential) { storage.putString(keyStorage, credential.value) }
    override fun clearCredential() { storage.remove(keyStorage) }
}
