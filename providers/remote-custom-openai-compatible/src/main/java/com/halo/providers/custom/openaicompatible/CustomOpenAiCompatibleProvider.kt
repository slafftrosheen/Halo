package com.halo.providers.custom.openaicompatible

import kotlinx.coroutines.flow.flowOn
import com.halo.sdk.provider.remoteAiContext

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
) : AiInferenceProvider, CredentialProvider by credentialProvider, com.halo.sdk.provider.ProviderConfigurationReporting {

    override val id: String = RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE
    override val displayName: String get() = "Custom OpenAI-compatible"
    override val isLocal: Boolean get() = endpointExecutionLocation(storage.getString("custom_openai_compatible_endpoint") ?: "http://localhost:8080/v1") == AiExecutionLocation.LOCAL_NETWORK

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

    override val configurationDescriptor: com.halo.sdk.provider.ProviderConfigurationDescriptor get() =
        configured.configurationDescriptor.copy(location = if (isLocal) AiExecutionLocation.LOCAL_NETWORK else AiExecutionLocation.REMOTE, fields = listOf(
            com.halo.sdk.provider.ProviderConfigurationField("credential", "API key / token", com.halo.sdk.provider.ProviderFieldKind.SECRET),
            com.halo.sdk.provider.ProviderConfigurationField("custom_openai_compatible_endpoint", "Endpoint", com.halo.sdk.provider.ProviderFieldKind.ENDPOINT), ProviderConfigurationField("custom_openai_compatible_model", "Model ID (optional if discovery works)", ProviderFieldKind.TEXT), ProviderConfigurationField("custom_openai_compatible_auth_mode", "Authentication", ProviderFieldKind.ENUM, listOf("NONE", "API_KEY", "BEARER_TOKEN"))))

    override val status: StateFlow<ProviderStatus> get() = configured.status
    val authInfo: StateFlow<ProviderAuthInfo> get() = configured.authInfo

    private fun syncEndpoint() {
        val endpoint = storage.getString("custom_openai_compatible_endpoint")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "http://localhost:8080/v1"
        transport.updateBaseUrl(endpoint)
    }

    override suspend fun getCapabilities(): Set<AiCapability> = configured.getCapabilities()
    override fun configurationChanged() = configured.configurationChanged()
    override suspend fun getStatus(): ProviderStatus = configured.getStatus()
    override suspend fun testConnection(): Boolean {
        syncEndpoint()
        return configured.testConnection()
    }
    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse =
        kotlinx.coroutines.withContext(storage.remoteAiContext(id, request, context)) {
        syncEndpoint()
        configured.generate(request, context)
    }
    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> {
        syncEndpoint()
        return configured.stream(request, context).flowOn(storage.remoteAiContext(id, request, context))
    }
    override suspend fun listModels(): List<AiModel> {
        syncEndpoint()
        val manual = storage.getString("custom_openai_compatible_model")?.trim()?.takeIf { it.isNotBlank() }?.let {
            AiModel(it, it, id, setOf(AiCapability.TEXT), null, supportsStreaming = true, supportsTools = false,
                executionLocation = configurationDescriptor.location)
        }
        val discovered = try { configured.listModels() }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) { if (manual == null) throw error else emptyList() }
        return (discovered.map { it.copy(executionLocation = configurationDescriptor.location) } + listOfNotNull(manual)).distinctBy { it.id }
    }
}

class CustomOpenAiCompatibleCredentialProvider(
    private val storage: Storage,
    private val fixedMode: CustomAuthMode? = null,
) : CredentialProvider {
    private val keyStorage = "custom_openai_compatible_credential"

    private fun mode(): CustomAuthMode = fixedMode ?: storage
        .getString("custom_openai_compatible_auth_mode")
        ?.let { raw -> runCatching { CustomAuthMode.valueOf(raw.trim().uppercase()) }.getOrNull() }
        ?: CustomAuthMode.NONE

    override fun hasCredential(): Boolean = when (mode()) {
        CustomAuthMode.NONE -> true
        CustomAuthMode.API_KEY, CustomAuthMode.BEARER_TOKEN -> storage.getString(keyStorage)?.takeIf { it.isNotBlank() } != null
    }

    override fun credential(): Credential? = when (mode()) {
        CustomAuthMode.NONE -> Credential(id = "custom.none", value = "", label = "No authentication")
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
