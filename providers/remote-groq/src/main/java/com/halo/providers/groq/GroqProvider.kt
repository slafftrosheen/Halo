package com.halo.providers.groq

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Groq preset over the shared OpenAI-compatible fabric.
 *
 * Auth: API key
 * Access: free tier available / pay-as-you-go
 * Protocol: OpenAI chat completions where the provider/model supports it.
 */
class GroqProvider(
    private val storage: Storage,
    private val transport: OpenAiCompatibleTransport = OpenAiCompatibleTransport.defaultClient().let {
        OpenAiCompatibleTransport(it, "https://api.groq.com/v1", AiWireProtocol.OPENAI_CHAT_COMPLETIONS)
    },
    private val credentialProvider: CredentialProvider = GroqCredentialProvider(storage),
) : AiInferenceProvider, CredentialProvider by credentialProvider {

    override val id: String = RemoteProviderIds.GROQ
    override val displayName: String = "Groq"
    override val isLocal: Boolean = false

    private val descriptor = RemoteProviderPresets.groq
    private val configured: ConfiguredOpenAiCompatibleProvider = ConfiguredOpenAiCompatibleProvider(
        descriptor = descriptor,
        transport = transport,
        credentialProvider = credentialProvider,
        systemPrompt = storage.getString("groq_system_prompt") ?: "You are Halo, a helpful AI assistant.",
        allowInsecureLocalCredentialTransport = false,
        freeModelIds = freePlanHints(),
    )

    override val status: StateFlow<ProviderStatus> get() = configured.status
    val authInfo: StateFlow<ProviderAuthInfo> get() = configured.authInfo

    override suspend fun getCapabilities(): Set<AiCapability> = configured.getCapabilities()
    override suspend fun getStatus(): ProviderStatus = configured.getStatus()
    override suspend fun testConnection(): Boolean = configured.testConnection()
    override suspend fun generate(request: AiRequest, context: AiContext): AiResponse = configured.generate(request, context)
    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = configured.stream(request, context)
    override suspend fun listModels(): List<AiModel> = configured.listModels()

    companion object {
        // Static fallback hints used only when remote discovery fails.
        // These are examples, not permanent truth: server discovery remains authoritative.
        private fun freePlanHints(): Set<String> = setOf(
            "gpt-oss",
        )

        fun createClient() = OpenAiCompatibleTransport.defaultClient()
    }
}

class GroqCredentialProvider(
    private val storage: Storage,
) : CredentialProvider {
    private val keyStorage = "groq_api_key"

    override fun hasCredential(): Boolean = storage.getString(keyStorage)?.takeIf { it.isNotBlank() } != null
    override fun credential(): Credential? = storage.getString(keyStorage)?.trim()?.ifBlank { null }?.let {
        Credential(id = "groq", value = it, label = "Groq API key")
    }
    override fun setCredential(credential: Credential) { storage.putString(keyStorage, credential.value) }
    override fun clearCredential() { storage.remove(keyStorage) }
}
