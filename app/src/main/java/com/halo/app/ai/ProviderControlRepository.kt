package com.halo.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.engines.ai.catalog.AiModelCatalogRepository
import com.halo.providers.gemini.GeminiProvider
import com.halo.providers.gemini.GeminiAuthenticationMode
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.URI

enum class ProviderConnectionState { NOT_CONFIGURED, CONFIGURED, CHECKING, READY, AUTH_FAILED, OFFLINE, RATE_LIMITED, ERROR }
enum class GenerationProof { NOT_TESTED, CHECKING, PROVEN, FAILED }
enum class ProviderCatalogueState { LIVE, CACHED, STALE, UNAVAILABLE }
data class ProviderControlRow(val descriptor: ProviderConfigurationDescriptor, val credentialConfigured: Boolean,
    val configured: Boolean, val selected: Boolean, val connection: ProviderConnectionState,
    val catalogue: ProviderCatalogueState, val modelCount: Int, val fields: Map<String, String> = emptyMap(),
    val generation: GenerationProof = GenerationProof.NOT_TESTED, val lastCheckedAt: Long? = null, val lastError: String? = null)

/** Single configuration owner. Saving never selects a provider or refreshes its remote catalogue. */
class ProviderControlRepository(val router: AiProviderRouter, val catalogue: AiModelCatalogRepository, private val storage: Storage) : ViewModel() {
    val providers = router.getAllProviders().filterIsInstance<AiInferenceProvider>()
    private val checked = java.util.concurrent.ConcurrentHashMap<String, ProviderConnectionState>()
    private val revisions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val testing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val generation = java.util.concurrent.ConcurrentHashMap<String, GenerationProof>()
    private val checkedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val errors = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val probing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val refreshed = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val _rows = MutableStateFlow<List<ProviderControlRow>>(emptyList())
    val rows: StateFlow<List<ProviderControlRow>> = _rows.asStateFlow()
    init {
        viewModelScope.launch(Dispatchers.IO) {
            catalogue.refresh("provider.litert.local") // Installed inventory only; no remote discovery or engine load.
            launch { combine(providers.map { it.status }) { Unit }.collect { reload() } }
            combine(router.selectedProviderId, catalogue.state) { _, _ -> Unit }.collect { reload() }
        }
    }
    private fun descriptor(provider: AiInferenceProvider): ProviderConfigurationDescriptor {
        (provider as? ProviderConfigurationReporting)?.let { return it.configurationDescriptor }
        val preset = when (provider.id) {
            RemoteProviderIds.GROQ -> RemoteProviderPresets.groq
            RemoteProviderIds.CEREBRAS -> RemoteProviderPresets.cerebras
            RemoteProviderIds.XAI -> RemoteProviderPresets.xai
            RemoteProviderIds.OPENCODE_ZEN -> RemoteProviderPresets.opencodeZen
            RemoteProviderIds.OPENCODE_COMPANION -> RemoteProviderPresets.opencodeCompanion
            RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE -> RemoteProviderPresets.customOpenAiCompatible
            else -> null
        }
        val fields = buildList {
            if (provider is CredentialProvider) add(ProviderConfigurationField("credential", "API key / token", ProviderFieldKind.SECRET))
            endpointKey(provider.id)?.let { add(ProviderConfigurationField(it, "Endpoint", ProviderFieldKind.ENDPOINT)) }
            if (provider.id == "gemini") {
                add(ProviderConfigurationField("gemini_auth_mode", "Authentication", ProviderFieldKind.ENUM, listOf("API_KEY", "OAUTH")))
                add(ProviderConfigurationField("gemini_project_id", "Google Cloud project ID", ProviderFieldKind.TEXT))
                add(ProviderConfigurationField("google_oauth", "Google OAuth", ProviderFieldKind.OAUTH_ACTION))
            }
            if (provider.id == RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE)
                add(ProviderConfigurationField("custom_openai_compatible_auth_mode", "Authentication", ProviderFieldKind.ENUM, listOf("NONE", "API_KEY", "BEARER_TOKEN")))
        }
        return ProviderConfigurationDescriptor(provider.id, provider.displayName,
            preset?.executionLocation ?: if (provider.id == "provider.litert.local") AiExecutionLocation.ON_DEVICE else if (provider.id == "provider.ollama") AiExecutionLocation.LOCAL_NETWORK else AiExecutionLocation.REMOTE,
            fields, preset?.protocol?.name ?: when (provider.id) { "anthropic" -> "ANTHROPIC_MESSAGES"; "gemini" -> "GEMINI_GENERATE_CONTENT"; "provider.ollama" -> "OLLAMA"; "provider.litert.local" -> "LITERT_NATIVE"; else -> "OPENAI_CHAT_COMPLETIONS" }, preset?.accessKind?.name ?: if (provider.isLocal) "LOCAL" else "ACCOUNT_DEPENDENT")
    }
    private fun reload() {
        _rows.value = providers.map { provider ->
            val descriptor = descriptor(provider)
            val nonSecrets = descriptor.fields.filter { it.kind != ProviderFieldKind.SECRET && it.kind != ProviderFieldKind.OAUTH_ACTION }
                .associate { it.id to (storage.getString(it.id) ?: it.options.firstOrNull().orEmpty()) }
            val hasKey = if (provider.id == RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE) !storage.getString("custom_openai_compatible_credential").isNullOrBlank() else (provider as? CredentialProvider)?.hasCredential() == true
            val oauth = (provider as? GeminiProvider)?.authenticationMode == GeminiAuthenticationMode.OAUTH
            val configured = when {
                provider.id == "provider.litert.local" -> true
                provider.id == "provider.ollama" -> !storage.getString("ollama_endpoint").isNullOrBlank()
                provider.id == RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE -> !storage.getString("custom_openai_compatible_endpoint").isNullOrBlank() &&
                    (nonSecrets["custom_openai_compatible_auth_mode"] == "NONE" || hasKey)
                provider.id == RemoteProviderIds.OPENCODE_COMPANION -> hasKey && !storage.getString("opencode_companion_endpoint").isNullOrBlank()
                else -> hasKey || oauth
            }
            val entries = catalogue.state.value.models.filter { it.model.providerId == provider.id }
            val at = refreshed[provider.id]
            val catalogState = when {
                entries.isEmpty() -> ProviderCatalogueState.UNAVAILABLE
                at == null || entries.all { it.source == ModelCatalogueSource.REMOTE_CACHED } -> ProviderCatalogueState.CACHED
                catalogue.state.value.providerErrors.containsKey(provider.id) || System.currentTimeMillis() - at > 24 * 60 * 60 * 1000L -> ProviderCatalogueState.STALE
                else -> ProviderCatalogueState.LIVE
            }
            val status = provider.status.value
            val connection = when {
                !configured -> ProviderConnectionState.NOT_CONFIGURED
                checked[provider.id] == ProviderConnectionState.CHECKING -> ProviderConnectionState.CHECKING
                status.cooldown != null -> ProviderConnectionState.RATE_LIMITED
                status.state == ProviderStatus.State.OFFLINE -> ProviderConnectionState.OFFLINE
                status.state == ProviderStatus.State.ERROR -> if (status.rejectionReason == TargetRejectionReason.CONFIGURATION_MISSING) ProviderConnectionState.AUTH_FAILED else ProviderConnectionState.ERROR
                provider.isLocal && status.state == ProviderStatus.State.READY -> ProviderConnectionState.READY
                else -> checked[provider.id] ?: ProviderConnectionState.CONFIGURED
            }
            ProviderControlRow(descriptor, hasKey, configured, router.selectedProviderId.value == provider.id,
                connection,
                catalogState, entries.size, nonSecrets, generation[provider.id] ?: GenerationProof.NOT_TESTED, checkedAt[provider.id], errors[provider.id] ?: when (connection) { ProviderConnectionState.AUTH_FAILED, ProviderConnectionState.ERROR, ProviderConnectionState.OFFLINE, ProviderConnectionState.RATE_LIMITED -> connection.name; else -> null })
        }
    }
    fun save(providerId: String, values: Map<String, String>, credentialDraft: String?, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = providers.firstOrNull { it.id == providerId } ?: return@launch
            try {
                val descriptor = descriptor(provider)
                val allowed = descriptor.fields.filter { it.kind in setOf(ProviderFieldKind.TEXT, ProviderFieldKind.ENDPOINT, ProviderFieldKind.ENUM) }
                allowed.forEach { field -> values[field.id]?.let { value ->
                    if (field.kind == ProviderFieldKind.ENDPOINT && value.isNotBlank()) {
                        val uri = URI(value.trim()); require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null)
                        require(CredentialTransportPolicy.evaluate(value.trim(), allowInsecureLocal = true) != CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT)
                    }
                    require(field.kind != ProviderFieldKind.ENUM || value in field.options)
                } }
                allowed.forEach { field -> values[field.id]?.let { storage.putString(field.id, it.trim()) } }
                if (!credentialDraft.isNullOrBlank()) (provider as? CredentialProvider)?.setCredential(Credential(providerId, credentialDraft.trim()))
                if (provider is GeminiProvider && values["gemini_auth_mode"] == "API_KEY") provider.clearOAuthSession()
                provider.configurationChanged(); revisions.merge(providerId, 1L) { previous, delta -> previous + delta }; refreshed.remove(providerId); checked.remove(providerId); generation.remove(providerId); checkedAt.remove(providerId); errors.remove(providerId); reload(); withContext(Dispatchers.Main) { onDone(true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { withContext(Dispatchers.Main) { onDone(false) } }
        }
    }
    fun removeCredential(providerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val provider = providers.firstOrNull { it.id == providerId }
        (provider as? CredentialProvider)?.clearCredential()
        (provider as? GeminiProvider)?.clearOAuthSession()
        provider?.configurationChanged(); revisions.merge(providerId, 1L) { previous, delta -> previous + delta }; refreshed.remove(providerId); checked.remove(providerId); generation.remove(providerId); checkedAt.remove(providerId); errors.remove(providerId); reload()
    }
    fun acceptGeminiOAuth(token: String, project: String) = viewModelScope.launch(Dispatchers.IO) {
        require(project.isNotBlank()); storage.putString("gemini_project_id", project.trim()); storage.putString("gemini_auth_mode", "OAUTH")
        (providers.firstOrNull { it is GeminiProvider } as? GeminiProvider)?.acceptOAuthAccessToken(token, project.trim())
        revisions.merge("gemini", 1L) { previous, delta -> previous + delta }; refreshed.remove("gemini"); checked.remove("gemini"); generation.remove("gemini"); checkedAt.remove("gemini"); errors.remove("gemini"); reload()
    }
    fun disconnectGeminiOAuth() = viewModelScope.launch(Dispatchers.IO) {
        (providers.firstOrNull { it is GeminiProvider } as? GeminiProvider)?.clearOAuthSession(); revisions.merge("gemini", 1L) { previous, delta -> previous + delta }; refreshed.remove("gemini"); checked.remove("gemini"); generation.remove("gemini"); checkedAt.remove("gemini"); errors.remove("gemini"); reload()
    }
    fun refreshModels(providerId: String) = viewModelScope.launch(Dispatchers.IO) {
        catalogue.refresh(providerId)
        if (!catalogue.state.value.providerErrors.containsKey(providerId)) refreshed[providerId] = System.currentTimeMillis()
        reload()
    }
    fun testConnection(providerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return@launch
        if (_rows.value.firstOrNull { it.descriptor.providerId == providerId }?.configured != true) return@launch
        if (!testing.add(providerId)) return@launch
        val revision = revisions[providerId] ?: 0L
        checked[providerId] = ProviderConnectionState.CHECKING; reload()
        try {
            checked[providerId] = if (withTimeout(20_000) { provider.testConnection() }) ProviderConnectionState.READY else {
                val status = provider.getStatus()
                when { status.cooldown != null -> ProviderConnectionState.RATE_LIMITED
                    status.rejectionReason == TargetRejectionReason.NETWORK_UNAVAILABLE -> ProviderConnectionState.OFFLINE
                    status.state == ProviderStatus.State.OFFLINE -> ProviderConnectionState.OFFLINE
                    status.message?.contains("auth", true) == true || status.message?.contains("key", true) == true -> ProviderConnectionState.AUTH_FAILED
                    status.message?.contains("rate", true) == true -> ProviderConnectionState.RATE_LIMITED
                    else -> ProviderConnectionState.ERROR }
            }
        } catch (cancelled: CancellationException) { checked[providerId] = ProviderConnectionState.ERROR; throw cancelled }
        catch (error: AiProviderException) { checked[providerId] = when(error.type) { AiErrorType.AUTHENTICATION_FAILED -> ProviderConnectionState.AUTH_FAILED; AiErrorType.RATE_LIMITED -> ProviderConnectionState.RATE_LIMITED; AiErrorType.NETWORK_ERROR, AiErrorType.NETWORK_UNAVAILABLE -> ProviderConnectionState.OFFLINE; else -> ProviderConnectionState.ERROR } }
        catch (_: Exception) { checked[providerId] = ProviderConnectionState.ERROR }
        finally {
            if ((revisions[providerId] ?: 0L) != revision) checked.remove(providerId)
            if ((revisions[providerId] ?: 0L) == revision) { checkedAt[providerId] = System.currentTimeMillis(); if (checked[providerId] == ProviderConnectionState.READY) errors.remove(providerId) else errors[providerId] = checked[providerId]?.name ?: "Connection check failed" }
            testing.remove(providerId); reload()
        }
    }
    /** User-triggered, empty context, no tools or history; never changes selection. */
    fun checkGeneration(providerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return@launch
        if (_rows.value.firstOrNull { it.descriptor.providerId == providerId }?.configured != true || !probing.add(providerId)) return@launch
        val revision = revisions[providerId] ?: 0L
        generation[providerId] = GenerationProof.CHECKING; reload()
        try {
            val model = catalogue.state.value.models.firstOrNull { it.model.providerId == providerId && it.model.id == router.selectedModelId.value }
                ?: catalogue.state.value.models.firstOrNull { it.model.providerId == providerId && AiCapability.TEXT in it.model.inputCapabilities }
            check(model != null) { "Refresh provider models first" }
            val response = withTimeout(30_000) { provider.generate(AiRequest(text = "Reply exactly HALO", modelId = model.model.id,
                maxOutputTokens = 8, toolUseMode = ToolUseMode.NONE, publishStreamingTokens = false), AiContext.EMPTY) }
            check(response.toolCalls.isEmpty() && response.text.trim() == "HALO") { "Generation check did not return the expected response" }
            if ((revisions[providerId] ?: 0L) == revision) { generation[providerId] = GenerationProof.PROVEN; errors.remove(providerId) }
        } catch (cancelled: CancellationException) {
            if ((revisions[providerId] ?: 0L) == revision) generation[providerId] = GenerationProof.NOT_TESTED
            throw cancelled
        } catch (_: Exception) {
            if ((revisions[providerId] ?: 0L) == revision) { generation[providerId] = GenerationProof.FAILED; errors[providerId] = "Generation check failed; verify authentication and model access" }
        } finally { probing.remove(providerId); reload() }
    }
    fun useDefault(providerId: String): Boolean {
        val row = rows.value.firstOrNull { it.descriptor.providerId == providerId } ?: return false
        if (!row.configured) return false
        router.selectProvider(providerId) // Existing router restores the per-provider model preference.
        return true
    }
    companion object {
        private fun endpointKey(id: String): String? = when(id) {
            "provider.ollama" -> "ollama_endpoint"
            RemoteProviderIds.OPENCODE_COMPANION -> "opencode_companion_endpoint"
            RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE -> "custom_openai_compatible_endpoint"
            else -> null
        }
    }
}
