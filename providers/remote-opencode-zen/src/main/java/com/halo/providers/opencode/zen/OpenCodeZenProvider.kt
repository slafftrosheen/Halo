package com.halo.providers.opencode.zen

import kotlinx.coroutines.flow.flowOn

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
        OpenAiCompatibleTransport(it, "https://opencode.ai/inference/v1", AiWireProtocol.OPENAI_CHAT_COMPLETIONS)
    },
    private val credentialProvider: CredentialProvider = OpenCodeZenCredentialProvider(storage),
) : AiInferenceProvider, CredentialProvider by credentialProvider, ProviderConfigurationReporting {

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

    private val responsesConfigured = ConfiguredOpenAiCompatibleProvider(
        descriptor = descriptor.copy(protocol = AiWireProtocol.OPENAI_RESPONSES),
        transport = transport.withProtocol(AiWireProtocol.OPENAI_RESPONSES),
        credentialProvider = credentialProvider,
        systemPrompt = storage.getString("opencode_zen_system_prompt") ?: "You are Halo, a helpful AI assistant.",
        allowInsecureLocalCredentialTransport = false,
        freeModelIds = emptySet(),
    )
    private fun generationProvider(): ConfiguredOpenAiCompatibleProvider =
        if (storage.getString("opencode_zen_protocol") == AiWireProtocol.OPENAI_RESPONSES.name) responsesConfigured else configured

    override val configurationDescriptor: ProviderConfigurationDescriptor get() = configured.configurationDescriptor.copy(
        protocol = if (generationProvider() === responsesConfigured) AiWireProtocol.OPENAI_RESPONSES.name else AiWireProtocol.OPENAI_CHAT_COMPLETIONS.name,
        fields = configured.configurationDescriptor.fields + ProviderConfigurationField("opencode_zen_protocol", "API protocol", ProviderFieldKind.ENUM,
            listOf(AiWireProtocol.OPENAI_CHAT_COMPLETIONS.name, AiWireProtocol.OPENAI_RESPONSES.name)),
    )

    override val status: StateFlow<ProviderStatus> get() = configured.status
    val authInfo: StateFlow<ProviderAuthInfo> get() = configured.authInfo

    override suspend fun getCapabilities(): Set<AiCapability> = configured.getCapabilities()
    override fun configurationChanged() { configured.configurationChanged(); responsesConfigured.configurationChanged() }
    override suspend fun getStatus(): ProviderStatus = configured.getStatus()
    override suspend fun testConnection(): Boolean = configured.testConnection()
    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = kotlinx.coroutines.withContext(storage.remoteAiContext(id, request, context)) { generationProvider().generate(request, context) }
    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = generationProvider().stream(request, context).flowOn(storage.remoteAiContext(id, request, context))
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
