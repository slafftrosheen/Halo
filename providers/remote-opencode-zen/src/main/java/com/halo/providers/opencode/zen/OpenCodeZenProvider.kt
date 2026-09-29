package com.halo.providers.opencode.zen

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/**
 * OpenCode Zen preset over the shared OpenAI-compatible fabric.
 *
 * Auth: API key
 * Access: mixed (paid models and currently free models).
 *
 * Free presence is REMOTE_LIVE evidence from current catalog metadata. This
 * provider is NOT labelled free wholesale.
 */
class OpenCodeZenProvider(
    private val storage: Storage,
    private val transport: OpenAiCompatibleTransport = OpenAiCompatibleTransport.defaultClient().let {
        OpenAiCompatibleTransport(it, "https://api.opencode.ai/v1", AiWireProtocol.OPENAI_CHAT_COMPLETIONS)
    },
    private val credentialProvider: CredentialProvider = OpenCodeZenCredentialProvider(storage),
) : AiInferenceProvider, CredentialProvider by credentialProvider {

    override val id: String = RemoteProviderIds.OPENCODE_ZEN
    override val displayName: String = "OpenCode Zen"
    override val isLocal: Boolean = false

    // Zen shares the preset transport directly; free/paid metadata is
    // determined per model from the current remote catalog by the shared
    // transport — no provider-wide free label is applied here.
    private val transportWithZenCatalog: OpenAiCompatibleTransport = transport

    private val descriptor = RemoteProviderPresets.opencodeZen
    private val configured: ConfiguredOpenAiCompatibleProvider = ConfiguredOpenAiCompatibleProvider(
        descriptor = descriptor,
        transport = transportWithZenCatalog,
        credentialProvider = credentialProvider,
        systemPrompt = storage.getString("opencode_zen_system_prompt") ?: "You are Halo, a helpful AI assistant.",
        allowInsecureLocalCredentialTransport = false,
        // Free model ids are determined from remote catalog, not baked in.
        freeModelIds = emptySet(),
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

class OpenCodeZenCredentialProvider(
    private val storage: Storage,
) : CredentialProvider {
    private val keyStorage = "opencode_zen_api_key"

    override fun hasCredential(): Boolean = storage.getString(keyStorage)?.takeIf { it.isNotBlank() } != null
    override fun credential(): Credential? = storage.getString(keyStorage)?.trim()?.ifBlank { null }?.let {
        Credential(id = "opencode.zen", value = it, label = "OpenCode Zen API key")
    }
    override fun setCredential(credential: Credential) { storage.putString(keyStorage, credential.value) }
    override fun clearCredential() { storage.remove(keyStorage) }
}
