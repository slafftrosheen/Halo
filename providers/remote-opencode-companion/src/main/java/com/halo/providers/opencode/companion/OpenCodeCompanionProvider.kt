package com.halo.providers.opencode.companion

import kotlinx.coroutines.flow.flowOn
import com.halo.sdk.provider.remoteAiContext

import com.halo.sdk.provider.AiCapability
import com.halo.sdk.provider.AiContext
import com.halo.sdk.provider.AiErrorType
import com.halo.sdk.provider.AiFailureDetails
import com.halo.sdk.provider.AiInferenceProvider
import com.halo.sdk.provider.AiModel
import com.halo.sdk.provider.AiProviderException
import com.halo.sdk.provider.AiRequest
import com.halo.sdk.provider.AiResponse
import com.halo.sdk.provider.AiStreamEvent
import com.halo.sdk.provider.AiWireProtocol
import com.halo.sdk.provider.Credential
import com.halo.sdk.provider.CredentialProvider
import com.halo.sdk.provider.CredentialTransportPolicy
import com.halo.sdk.provider.CredentialTransportResult
import com.halo.sdk.provider.ConfiguredOpenAiCompatibleProvider
import com.halo.sdk.provider.OpenAiCompatibleTransport
import com.halo.sdk.provider.ProviderAuthInfo
import com.halo.sdk.provider.ProviderAuthState
import com.halo.sdk.provider.ProviderSecretStore
import com.halo.sdk.provider.ProviderStatus
import com.halo.sdk.provider.RemoteProviderIds
import com.halo.sdk.provider.RemoteProviderPresets
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.cancellation.CancellationException

/**
 * OpenCode Companion provider.
 *
 * Execution location: LOCAL_NETWORK when private/Tailscale/LAN. If the user
 * explicitly points it to a remote public host, execution is REMOTE.
 *
 * Purpose: Halo connects to a USER-RUNNING OpenCode server. OpenCode owns its
 * own provider credentials. Halo never sees ChatGPT/xAI/Zen/OpenRouter
 * credentials. Halo stores only the endpoint, preferred provider/model and a
 * local auth credential. The password belongs in the secret store and is never
 * backed up in Brain snapshots.
 *
 * For ordinary Halo inference turns, OpenCode companion supplies model
 * inference only. OpenCode tools (shell/fs/MCP/browser/phone) are disabled for
 * normal Halo inference where the companion API supports it.
 */
class OpenCodeCompanionProvider(
    private val storage: Storage,
    private val secretStore: ProviderSecretStore,
    private val client: OkHttpClient = OpenAiCompatibleTransport.defaultClient(),
    private val companionCredentialProvider: CompanionCredentialProvider =
        CompanionCredentialProvider(storage, secretStore),
) : AiInferenceProvider, CredentialProvider by companionCredentialProvider, com.halo.sdk.provider.ProviderConfigurationReporting {

    override val id: String = RemoteProviderIds.OPENCODE_COMPANION
    override val displayName: String = "OpenCode Companion"
    override val isLocal: Boolean get() = com.halo.sdk.provider.endpointExecutionLocation(companionEndpoint()) == com.halo.sdk.provider.AiExecutionLocation.LOCAL_NETWORK

    private val descriptor = RemoteProviderPresets.opencodeCompanion

    private val transport: OpenAiCompatibleTransport by lazy {
        OpenAiCompatibleTransport(client, companionEndpoint(), AiWireProtocol.OPENAI_CHAT_COMPLETIONS)
    }

    private val configured: ConfiguredOpenAiCompatibleProvider by lazy {
        ConfiguredOpenAiCompatibleProvider(
            descriptor = descriptor,
            transport = transport,
            credentialProvider = companionCredentialProvider,
            systemPrompt = storage.getString("opencode_companion_system_prompt") ?: "You are Halo, a helpful AI assistant.",
            allowInsecureLocalCredentialTransport = true,
            fallbackWhenUnauthenticated = false,
            staticHints = companionStaticHints(),
        )
    }

    override val configurationDescriptor: com.halo.sdk.provider.ProviderConfigurationDescriptor get() =
        configured.configurationDescriptor.copy(location = com.halo.sdk.provider.endpointExecutionLocation(companionEndpoint()), fields = listOf(
            com.halo.sdk.provider.ProviderConfigurationField("credential", "API key / token", com.halo.sdk.provider.ProviderFieldKind.SECRET),
            com.halo.sdk.provider.ProviderConfigurationField("opencode_companion_endpoint", "Endpoint", com.halo.sdk.provider.ProviderFieldKind.ENDPOINT)))

    override val status: StateFlow<ProviderStatus> get() = configured.status
    val authInfo: StateFlow<ProviderAuthInfo> get() = configured.authInfo

    private fun syncEndpoint() {
        transport.updateBaseUrl(companionEndpoint())
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
        companionGenerate(request, context)
    }
    override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> {
        syncEndpoint()
        return companionStream(request, context).flowOn(storage.remoteAiContext(id, request, context))
    }
    override suspend fun listModels(): List<AiModel> {
        syncEndpoint()
        return companionListModels().map { it.copy(executionLocation = configurationDescriptor.location) }
    }

    /** Health diagnostics from the OpenCode server health endpoint. */
    suspend fun health(): CompanionHealth = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        return@withContext try {
            val url = "${healthBaseUrl(companionEndpoint())}/health"
            // Companion health never carries credentials, but still refuse
            // public cleartext hosts to keep endpoint discipline consistent.
            val security = CredentialTransportPolicy.evaluate(url, allowInsecureLocal = true)
            if (security == CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT) {
                CompanionHealth(
                    reachable = false,
                    serverVersion = null,
                    authenticated = hasCompanionCredential(),
                    latencyMs = System.currentTimeMillis() - start,
                    warning = "Public cleartext OpenCode endpoint is not permitted",
                )
            } else {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        CompanionHealth(
                            reachable = false,
                            serverVersion = null,
                            authenticated = hasCompanionCredential(),
                            latencyMs = System.currentTimeMillis() - start,
                            warning = "OpenCode health returned HTTP ${response.code}",
                        )
                    } else {
                        val body = response.body?.string()
                        val version = body?.let { runCatching { JSONObject(it).optString("version") }.getOrNull() }
                        CompanionHealth(
                            reachable = true,
                            serverVersion = version?.takeIf { it.isNotBlank() },
                            authenticated = hasCompanionCredential(),
                            latencyMs = System.currentTimeMillis() - start,
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CompanionHealth(
                reachable = false,
                serverVersion = null,
                authenticated = hasCompanionCredential(),
                latencyMs = System.currentTimeMillis() - start,
                warning = e.message ?: "OpenCode health check failed",
            )
        }
    }

    /** Discover underlying providers/models from OpenCode's provider/config API. */
    suspend fun discoverProvidersAndModels(): CompanionDiscoveryResult = withContext(Dispatchers.IO) {
        try {
            val url = "${healthBaseUrl(companionEndpoint())}/api/providers?includeModels=true"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    CompanionDiscoveryResult.Failed("OpenCode provider discovery returned HTTP ${response.code}")
                } else {
                    val body = response.body?.string() ?: return@withContext CompanionDiscoveryResult.Failed("Empty discovery body")
                    CompanionDiscoveryResult.Ok(parseDiscoveryProviders(JSONObject(body)))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            CompanionDiscoveryResult.Failed(e.message ?: "OpenCode discovery failed")
        }
    }

    private suspend fun companionGenerate(request: AiRequest, context: AiContext): AiResponse {
        // OpenCode companion inference uses Halo's selected underlying provider/model.
        // Halo tool loop remains owner; companion tools are disabled for ordinary Halo turns.
        val cred = companionCredentialProvider.credential() ?: throw companionMissingCredential()
        val (header, value) = credentialHeader(cred)
        val model = request.modelId?.takeIf { it.isNotBlank() }
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "Select an OpenCode companion model first")
        return try {
            transport.generate(request, context, model, header, value, null, true).also {
                if (it.text.isNotBlank() || it.toolCalls.isNotEmpty()) setAuthState(ProviderAuthState.CONNECTED)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: AiProviderException) {
            if (e.type == AiErrorType.AUTHENTICATION_FAILED) setAuthState(ProviderAuthState.EXPIRED, e.message)
            throw e
        }
    }

    private fun companionStream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = flow {
        val cred = companionCredentialProvider.credential() ?: throw companionMissingCredential()
        val (header, value) = credentialHeader(cred)
        val model = request.modelId?.takeIf { it.isNotBlank() }
            ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "Select an OpenCode companion model first")
        transport.stream(request, context, model, header, value, null, true).collect { emit(it) }
    }

    private suspend fun companionListModels(): List<AiModel> {
        // Companion models are discovered from the companion, with underlying
        // provider prefixes so same-named models cannot collide.
        return when (val result = discoverProvidersAndModels()) {
            is CompanionDiscoveryResult.Ok -> result.providers.flatMap { mirror ->
                mirror.models.map { model ->
                    AiModel(
                        id = model.id,
                        displayName = model.displayName,
                        providerId = id,
                        inputCapabilities = setOf(AiCapability.TEXT),
                        contextLength = null,
                        supportsStreaming = true,
                        supportsTools = false,
                        isFree = model.isFree,
                    )
                }
            }
            is CompanionDiscoveryResult.Failed -> configured.listModels()
        }
    }

    private fun companionEndpoint(): String =
        storage.getString("opencode_companion_endpoint")?.trim()?.ifBlank { null } ?: "http://localhost:8080"

    private fun healthBaseUrl(endpoint: String): String = endpoint.trimEnd('/').removeSuffix("/api")

    private fun credentialHeader(cred: Credential): Pair<String, String> = when (cred.id) {
        "companion.basic" -> Pair("Authorization", "Basic ${cred.value}")
        else -> Pair("Authorization", "Bearer ${cred.value}")
    }

    private fun companionMissingCredential() = AiProviderException(
        AiErrorType.AUTHENTICATION_FAILED,
        "OpenCode companion is not configured",
        AiFailureDetails(recoverability = com.halo.sdk.provider.Recoverability.USER_ACTION_REQUIRED),
    )

    private fun hasCompanionCredential(): Boolean = companionCredentialProvider.hasCredential()

    private fun companionStaticHints(): List<AiModel> = listOf(
        AiModel(
            id = "companion.defaults",
            displayName = "OpenCode companion defaults",
            providerId = id,
            inputCapabilities = setOf(AiCapability.TEXT),
            contextLength = null,
            supportsStreaming = true,
            supportsTools = false,
            isFree = false,
        ),
    )

    private fun setAuthState(state: ProviderAuthState, failure: String? = null) {
        configured.setAuthState(state, failure)
    }

    companion object {
        private fun parseDiscoveryProviders(json: JSONObject): List<CompanionProviderMirror> {
            val list = json.optJSONArray("providers") ?: JSONArray()
            return buildList {
                for (i in 0 until list.length()) {
                    val provider = list.optJSONObject(i) ?: continue
                    val underlying = provider.optString("provider", "unknown")
                    val models = provider.optJSONArray("models") ?: JSONArray()
                    val modelEntries = buildList {
                        for (j in 0 until models.length()) {
                            val model = models.optJSONObject(j) ?: continue
                            val modelId = model.optString("id")
                            add(
                                CompanionModelMirror(
                                    id = if (modelId.isNotBlank()) "$underlying/$modelId" else modelId,
                                    displayName = model.optString("name", modelId),
                                    isFree = model.optBoolean("free", false),
                                ),
                            )
                        }
                    }
                    add(
                        CompanionProviderMirror(
                            underlyingProvider = underlying,
                            models = modelEntries,
                        ),
                    )
                }
            }
        }
    }
}

data class CompanionHealth(
    val reachable: Boolean,
    val serverVersion: String?,
    val authenticated: Boolean,
    val latencyMs: Long,
    val warning: String? = null,
)

sealed interface CompanionDiscoveryResult {
    data class Ok(val providers: List<CompanionProviderMirror>) : CompanionDiscoveryResult
    data class Failed(val reason: String) : CompanionDiscoveryResult
}

data class CompanionProviderMirror(
    val underlyingProvider: String,
    val models: List<CompanionModelMirror>,
)

data class CompanionModelMirror(
    val id: String,
    val displayName: String,
    val isFree: Boolean,
)

class CompanionCredentialProvider(
    private val storage: Storage,
    private val secretStore: ProviderSecretStore,
) : CredentialProvider {
    private val configKey = "opencode_companion_credential"
    private val passwordSecretKey = "opencode_companion_password"

    override fun hasCredential(): Boolean = storage.getString(configKey)?.takeIf { it.isNotBlank() } != null ||
        kotlinx.coroutines.runBlocking { runCatching { secretStore.get(passwordSecretKey) }.getOrNull() }
            ?.takeIf { it.isNotBlank() } != null

    override fun credential(): Credential? {
        val config = storage.getString(configKey)
        if (!config.isNullOrBlank()) {
            return try {
                JSONObject(config).let { json ->
                    Credential(
                        id = json.optString("mode", "companion.token"),
                        value = when (json.optString("mode", "companion.token")) {
                            "companion.token" -> json.optString("token", "")
                            "companion.basic" ->
                                "user:${json.optString("user", "")}:${kotlinx.coroutines.runBlocking { runCatching { secretStore.get(passwordSecretKey) }.getOrNull()?.trim() ?: "" }}"
                            else -> json.optString("token", "")
                        },
                        label = "OpenCode companion credential",
                    )
                }
            } catch (e: Exception) {
                null
            }
        }
        return kotlinx.coroutines.runBlocking { runCatching { secretStore.get(passwordSecretKey) }.getOrNull() }
            ?.trim()?.ifBlank { null }?.let {
                Credential(id = "companion.basic", value = it, label = "OpenCode companion password")
            }
    }

    override fun setCredential(credential: Credential) {
        when (credential.id) {
            "companion.basic" -> {
                storage.putString(configKey, JSONObject().put("mode", "companion.basic").put("user", "halo").toString())
                kotlinx.coroutines.runBlocking { secretStore.put(passwordSecretKey, credential.value) }
            }
            else -> storage.putString(configKey, JSONObject().put("mode", "companion.token").put("token", credential.value).toString())
        }
    }

    override fun clearCredential() {
        storage.remove(configKey)
        kotlinx.coroutines.runBlocking { secretStore.remove(passwordSecretKey) }
    }
}
