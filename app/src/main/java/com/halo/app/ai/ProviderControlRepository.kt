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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ProviderConnectionState { NOT_CONFIGURED, CONFIGURED, CHECKING, READY, AUTH_FAILED, OFFLINE, RATE_LIMITED, ERROR }
enum class GenerationProof { NOT_TESTED, CHECKING, PROVEN, FAILED }
enum class ProviderCatalogueState { LIVE, CACHED, STALE, UNAVAILABLE }
data class ProviderControlRow(val descriptor: ProviderConfigurationDescriptor, val credentialConfigured: Boolean,
    val configured: Boolean, val selected: Boolean, val connection: ProviderConnectionState,
    val catalogue: ProviderCatalogueState, val modelCount: Int, val fields: Map<String, String> = emptyMap(),
    val generation: GenerationProof = GenerationProof.NOT_TESTED, val lastCheckedAt: Long? = null, val lastError: String? = null, val lastSelectedModelId: String? = null, val generationModelId: String? = null, val diagnostic: ProviderTestDiagnostic? = null, val generationEvidence: ProviderGenerationEvidence? = null)

/** Single configuration owner. Saving never selects a provider or refreshes its remote catalogue. */
class ProviderControlRepository(val router: AiProviderRouter, val catalogue: AiModelCatalogRepository, private val storage: Storage) : ViewModel() {
    val providers = router.getAllProviders().filterIsInstance<AiInferenceProvider>()
    private val configurationWrites = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val diagnostics = java.util.concurrent.ConcurrentHashMap<String, ProviderTestDiagnostic>()
    private val proofs = java.util.concurrent.ConcurrentHashMap<String, ProviderGenerationEvidence>()
    fun lastDiagnostic(providerId: String): ProviderTestDiagnostic? = diagnostics[providerId]
    private fun endpointHost(providerId: String): String? = providers.firstOrNull { it.id == providerId }?.let(::descriptor)
        ?.fields?.firstOrNull { it.kind == ProviderFieldKind.ENDPOINT }?.let { runCatching { URI(storage.getString(it.id).orEmpty()).host }.getOrNull() }
    private fun selectedModelId(providerId: String): String? =
        if (router.selectedProviderId.value == providerId) router.selectedModelId.value else storage.getString("ai_last_model_$providerId")
    private fun invalidateProof(providerId: String) {
        revisions.merge(providerId, 1L) { previous, delta -> previous + delta }
        refreshed.remove(providerId); checked.remove(providerId); generation.remove(providerId)
        generationModels.remove(providerId); generationSelections.remove(providerId); checkedAt.remove(providerId); errors.remove(providerId); proofs.remove(providerId)
        catalogue.invalidate(providerId)
    }
    private val checked = java.util.concurrent.ConcurrentHashMap<String, ProviderConnectionState>()
    private val revisions = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val testing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val generation = java.util.concurrent.ConcurrentHashMap<String, GenerationProof>()
    private val generationModels = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val generationSelections = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val checkedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val errors = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val probing = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val refreshed = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val _rows = MutableStateFlow<List<ProviderControlRow>>(emptyList())
    val rows: StateFlow<List<ProviderControlRow>> = _rows.asStateFlow()
    /** One cached projection shared by Model Browser, Roles and System Check. */
    val roleCandidates: StateFlow<Map<AiModelRole, List<AiRoleCandidate>>> = combine(
        catalogue.state, rows, router.roleBindings, router.routingPolicy,
    ) { catalog, providerRows, _, _ ->
        val configured = providerRows.filter { it.configured }.map { it.descriptor.providerId }.toSet()
        AiModelRole.entries.associateWith { router.candidatesForRole(it, catalog.models, configured) }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun roleTarget(role: AiModelRole, candidates: Map<AiModelRole, List<AiRoleCandidate>> = roleCandidates.value): AiRoleCandidate? {
        val binding = router.roleBinding(role)
        val available = candidates[role].orEmpty()
        if (role != AiModelRole.CHAT && binding.selection == AiRoleSelection.DISABLED) return null
        val provider = if (role == AiModelRole.CHAT || binding.selection == AiRoleSelection.INHERIT_DEFAULT) router.selectedProviderId.value else binding.providerId
        val model = if (role == AiModelRole.CHAT || binding.selection == AiRoleSelection.INHERIT_DEFAULT) router.selectedModelId.value else binding.modelId
        if (role == AiModelRole.CHAT || binding.selection != AiRoleSelection.AUTOMATIC)
            return available.firstOrNull { it.ref == AiModelRef(provider.orEmpty(), model.orEmpty()) }
        return available.firstOrNull { it.eligible }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            catalogue.restoreCached()
            catalogue.refresh("provider.litert.local") // Installed inventory only; no remote discovery or engine load.
            launch { combine(providers.map { it.status }) { Unit }.collect { reload() } }
            combine(router.selectedProviderId, router.selectedModelId, catalogue.state) { _, _, _ -> Unit }.collect { reload() }
        }
    }
    private fun descriptor(provider: AiInferenceProvider): ProviderConfigurationDescriptor {
        (provider as? ProviderConfigurationReporting)?.let { return it.configurationDescriptor }
        // Unconfigurable registry entries may still be represented, but setup belongs to providers.
        return ProviderConfigurationDescriptor(provider.id, provider.displayName,
            if (provider.isLocal) AiExecutionLocation.ON_DEVICE else AiExecutionLocation.REMOTE,
            emptyList(), "UNKNOWN", "ACCOUNT_DEPENDENT")
    }
    private fun reload() {
        _rows.value = providers.map { provider ->
            val descriptor = descriptor(provider)
            val nonSecrets = descriptor.fields.filter { it.kind != ProviderFieldKind.SECRET && it.kind != ProviderFieldKind.OAUTH_ACTION }
                .associate { it.id to (storage.getString(it.id) ?: it.options.firstOrNull().orEmpty()) }
            val credentialRead = runCatching { if (provider.id == RemoteProviderIds.CUSTOM_OPENAI_COMPATIBLE) !storage.getString("custom_openai_compatible_credential").isNullOrBlank() else (provider as? CredentialProvider)?.hasCredential() == true }
            val hasKey = credentialRead.getOrDefault(false)
            val readFailure = credentialRead.exceptionOrNull()?.let { providerFailure(ProviderTestStage.SAVE_SECURELY, it) }
            val geminiMode = runCatching { (provider as? GeminiProvider)?.authenticationMode }.getOrNull()
            val oauth = geminiMode == GeminiAuthenticationMode.OAUTH
            val configured = when {
                provider is GeminiProvider -> geminiMode != null && geminiMode != GeminiAuthenticationMode.NONE
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
                readFailure != null -> ProviderConnectionState.ERROR
                !configured -> ProviderConnectionState.NOT_CONFIGURED
                checked[provider.id] == ProviderConnectionState.CHECKING -> ProviderConnectionState.CHECKING
                descriptor.location == AiExecutionLocation.REMOTE -> checked[provider.id] ?: ProviderConnectionState.CONFIGURED
                status.cooldown?.isActive() == true -> ProviderConnectionState.RATE_LIMITED
                status.state == ProviderStatus.State.OFFLINE -> ProviderConnectionState.OFFLINE
                status.state == ProviderStatus.State.ERROR -> if (status.rejectionReason == TargetRejectionReason.CONFIGURATION_MISSING) ProviderConnectionState.AUTH_FAILED else ProviderConnectionState.ERROR
                provider.isLocal && status.state == ProviderStatus.State.READY -> ProviderConnectionState.READY
                else -> checked[provider.id] ?: ProviderConnectionState.CONFIGURED
            }
            val selectedModel = selectedModelId(provider.id)
            if (generation[provider.id] == GenerationProof.PROVEN && generationSelections[provider.id] != selectedModel) {
                generation.remove(provider.id); generationModels.remove(provider.id); generationSelections.remove(provider.id); proofs.remove(provider.id)
                if (diagnostics[provider.id]?.stage == ProviderTestStage.TEST_GENERATION) diagnostics.remove(provider.id)
            }
            ProviderControlRow(descriptor, hasKey, configured, router.selectedProviderId.value == provider.id,
                connection,
                catalogState, entries.size, nonSecrets, generation[provider.id] ?: GenerationProof.NOT_TESTED, checkedAt[provider.id], errors[provider.id] ?: when (connection) { ProviderConnectionState.AUTH_FAILED, ProviderConnectionState.ERROR, ProviderConnectionState.OFFLINE, ProviderConnectionState.RATE_LIMITED -> connection.name; else -> null },
                if (router.selectedProviderId.value == provider.id) router.selectedModelId.value else storage.getString("ai_last_model_${provider.id}"), generationModels[provider.id], readFailure ?: diagnostics[provider.id], proofs[provider.id])
        }
    }
    fun save(providerId: String, values: Map<String, String>, credentialDraft: String?, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val provider = providers.firstOrNull { it.id == providerId }
            if (provider == null) { withContext(Dispatchers.Main) { onDone(false) }; return@launch }
            configurationWrites.getOrPut(providerId) { Mutex() }.withLock {
                try {
                    val fields = descriptor(provider).fields.filter { it.kind in setOf(ProviderFieldKind.TEXT, ProviderFieldKind.ENDPOINT, ProviderFieldKind.ENUM) }
                    fields.forEach { field -> values[field.id]?.let { value ->
                        if (field.kind == ProviderFieldKind.ENDPOINT && value.isNotBlank()) {
                            val uri = runCatching { URI(value.trim()) }.getOrElse { throw IllegalArgumentException("Invalid provider address") }
                            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null && uri.rawFragment == null)
                            require(CredentialTransportPolicy.evaluate(value.trim(), allowInsecureLocal = true) != CredentialTransportResult.INSECURE_CREDENTIAL_TRANSPORT)
                        }
                        require(field.kind != ProviderFieldKind.ENUM || value in field.options)
                    } }
                    // Invalidate before writes: an in-flight check must never authenticate a newly saved key.
                    invalidateProof(providerId)
                    if (!credentialDraft.isNullOrBlank()) (provider as? CredentialProvider)
                        ?.setCredential(Credential(providerId, credentialDraft.trim()))
                    storage.putBatch(strings = fields.mapNotNull { field -> values[field.id]?.let { field.id to it.trim() } }.toMap())
                    if (provider is GeminiProvider && values["gemini_auth_mode"] == "API_KEY") provider.clearOAuthSession()
                    // Also invalidate checks started while the configuration write was in progress.
                    invalidateProof(providerId)
                    // A refresh failure after successful persistence is not a save failure.
                    runCatching { provider.configurationChanged() }
                    diagnostics[providerId] = ProviderTestDiagnostic(ProviderTestStage.SAVE_SECURELY, ProviderTestOutcome.SAVED_UNTESTED, endpointHost = endpointHost(providerId))
                    reload()
                    withContext(Dispatchers.Main) { onDone(true) }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    invalidateProof(providerId)
                    diagnostics[providerId] = providerFailure(ProviderTestStage.SAVE_SECURELY, error, endpointHost(providerId))
                    errors[providerId] = diagnostics.getValue(providerId).summary()
                    runCatching { reload() }
                    withContext(Dispatchers.Main) { onDone(false) }
                }
            }
        }
    }
    fun removeCredential(providerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val provider = providers.firstOrNull { it.id == providerId }
        (provider as? CredentialProvider)?.clearCredential()
        (provider as? GeminiProvider)?.clearOAuthSession()
        invalidateProof(providerId); diagnostics.remove(providerId); provider?.configurationChanged(); reload()
    }
    fun acceptGeminiOAuth(token: String, project: String) = viewModelScope.launch(Dispatchers.IO) {
        require(project.isNotBlank()); storage.putString("gemini_project_id", project.trim()); storage.putString("gemini_auth_mode", "OAUTH")
        (providers.firstOrNull { it is GeminiProvider } as? GeminiProvider)?.acceptOAuthAccessToken(token, project.trim())
        invalidateProof("gemini"); diagnostics["gemini"] = ProviderTestDiagnostic(ProviderTestStage.SAVE_SECURELY, ProviderTestOutcome.SAVED_UNTESTED); reload()
    }
    fun disconnectGeminiOAuth() = viewModelScope.launch(Dispatchers.IO) {
        (providers.firstOrNull { it is GeminiProvider } as? GeminiProvider)?.clearOAuthSession(); invalidateProof("gemini"); diagnostics.remove("gemini"); reload()
    }
    fun refreshModels(providerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val revision = revisions[providerId] ?: 0L
        diagnostics[providerId] = ProviderTestDiagnostic(ProviderTestStage.FETCH_MY_MODELS, ProviderTestOutcome.CHECKING)
        try {
            catalogue.refresh(providerId)
            if ((revisions[providerId] ?: 0L) != revision) return@launch
            val failure = catalogue.state.value.providerFailures[providerId]
            diagnostics[providerId] = if (failure != null) providerFailure(ProviderTestStage.FETCH_MY_MODELS,
                AiProviderException(failure.type, "Discovery failed", AiFailureDetails(httpStatus = failure.httpStatus, retryAfterMs = failure.retryAfterMs, providerCode = failure.providerCode)), endpointHost(providerId))
            else if (catalogue.state.value.providerErrors.containsKey(providerId)) ProviderTestDiagnostic(ProviderTestStage.FETCH_MY_MODELS, ProviderTestOutcome.PROTOCOL_ERROR)
            else ProviderTestDiagnostic(ProviderTestStage.FETCH_MY_MODELS, ProviderTestOutcome.MODELS_AVAILABLE, endpointHost = endpointHost(providerId))
            if (!catalogue.state.value.providerErrors.containsKey(providerId)) refreshed[providerId] = System.currentTimeMillis()
        } catch (cancelled: CancellationException) {
            if ((revisions[providerId] ?: 0L) == revision) diagnostics[providerId] = providerFailure(ProviderTestStage.FETCH_MY_MODELS, cancelled)
            throw cancelled
        } catch (error: Exception) {
            if ((revisions[providerId] ?: 0L) == revision) diagnostics[providerId] = providerFailure(ProviderTestStage.FETCH_MY_MODELS, error, endpointHost(providerId))
        } finally { reload() }
    }
    fun testConnection(providerId: String) = viewModelScope.launch(Dispatchers.IO) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return@launch
        if (_rows.value.firstOrNull { it.descriptor.providerId == providerId }?.configured != true || !testing.add(providerId)) return@launch
        val revision = revisions[providerId] ?: 0L
        checked[providerId] = ProviderConnectionState.CHECKING
        diagnostics[providerId] = ProviderTestDiagnostic(ProviderTestStage.AUTHENTICATE, ProviderTestOutcome.CHECKING)
        reload()
        var result = ProviderTestDiagnostic(ProviderTestStage.AUTHENTICATE, ProviderTestOutcome.PROTOCOL_ERROR)
        try {
            result = if (withTimeout(20_000) { provider.testConnection() }) ProviderTestDiagnostic(ProviderTestStage.AUTHENTICATE, ProviderTestOutcome.READY_AUTH, endpointHost = endpointHost(providerId))
            else ProviderTestDiagnostic(ProviderTestStage.AUTHENTICATE, ProviderTestOutcome.PROTOCOL_ERROR, endpointHost = endpointHost(providerId))
        } catch (cancelled: CancellationException) {
            result = providerFailure(ProviderTestStage.AUTHENTICATE, cancelled)
            throw cancelled
        } catch (error: Exception) { result = providerFailure(ProviderTestStage.AUTHENTICATE, error, endpointHost(providerId)) }
        finally {
            if ((revisions[providerId] ?: 0L) == revision) {
                diagnostics[providerId] = result
                checkedAt[providerId] = result.checkedAtMs
                checked[providerId] = when (result.outcome) {
                    ProviderTestOutcome.READY_AUTH -> ProviderConnectionState.READY
                    ProviderTestOutcome.AUTH_FAILED, ProviderTestOutcome.PERMISSION_DENIED -> ProviderConnectionState.AUTH_FAILED
                    ProviderTestOutcome.RATE_LIMITED -> ProviderConnectionState.RATE_LIMITED
                    ProviderTestOutcome.OFFLINE, ProviderTestOutcome.DNS_ERROR, ProviderTestOutcome.TLS_ERROR -> ProviderConnectionState.OFFLINE
                    else -> ProviderConnectionState.ERROR
                }
                if (result.outcome == ProviderTestOutcome.READY_AUTH) errors.remove(providerId) else errors[providerId] = result.summary()
            }
            testing.remove(providerId); reload()
        }
    }
    /** User-triggered, empty context, no tools or history; never changes selection. */
    fun checkGeneration(providerId: String, allowBillable: Boolean = false) = viewModelScope.launch(Dispatchers.IO) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return@launch
        if (_rows.value.firstOrNull { it.descriptor.providerId == providerId }?.configured != true || !probing.add(providerId)) return@launch
        if (!provider.isLocal && !allowBillable) {
            diagnostics[providerId] = ProviderTestDiagnostic(ProviderTestStage.TEST_GENERATION, ProviderTestOutcome.CONSENT_REQUIRED)
            probing.remove(providerId); reload(); return@launch
        }
        val revision = revisions[providerId] ?: 0L
        diagnostics[providerId] = ProviderTestDiagnostic(ProviderTestStage.TEST_GENERATION, ProviderTestOutcome.CHECKING)
        val selectedModel = selectedModelId(providerId)
        fun currentCheck() = (revisions[providerId] ?: 0L) == revision && selectedModelId(providerId) == selectedModel
        generationModels.remove(providerId); generation[providerId] = GenerationProof.CHECKING; reload()
        try {
            val model = catalogue.state.value.models.firstOrNull {
                it.model.providerId == providerId && it.model.id == selectedModel &&
                    it.kind == AiModelKind.CONCRETE && AiCapability.TEXT in it.model.inputCapabilities
            } ?: throw AiProviderException(AiErrorType.MODEL_NOT_SELECTED, "Choose a concrete text model first")
            val response = withTimeout(30_000) { provider.generate(AiRequest(text = "Reply exactly HALO", modelId = model.model.id,
                maxOutputTokens = 256, temperatureOverride = 0.0, toolUseMode = ToolUseMode.NONE, reasoningMode = AiReasoningMode.DISABLED, publishStreamingTokens = false), AiContext.EMPTY) }
            val evidence = generationEvidence(response)
            if (currentCheck()) proofs[providerId] = evidence
            if (!evidence.proven) throw AiProviderException(if (evidence.protocolSuccess) AiErrorType.EMPTY_RESPONSE else AiErrorType.INVALID_RESPONSE,
                "Generation check did not return a nonempty final answer")
            if (currentCheck()) { generationSelections[providerId] = model.model.id; generationModels[providerId] = response.resolvedModelId ?: response.modelId ?: model.model.id; generation[providerId] = GenerationProof.PROVEN; diagnostics[providerId] = ProviderTestDiagnostic(ProviderTestStage.TEST_GENERATION, ProviderTestOutcome.GENERATION_PROVEN, endpointHost = endpointHost(providerId)); errors.remove(providerId) }
        } catch (cancelled: CancellationException) {
            if (currentCheck()) {
                diagnostics[providerId] = providerFailure(ProviderTestStage.TEST_GENERATION, cancelled)
                generation[providerId] = if (cancelled is TimeoutCancellationException) GenerationProof.FAILED else GenerationProof.NOT_TESTED
            }
            throw cancelled
        } catch (error: Exception) {
            if (currentCheck()) { diagnostics[providerId] = providerFailure(ProviderTestStage.TEST_GENERATION, error, endpointHost(providerId)); generation[providerId] = GenerationProof.FAILED; errors[providerId] = "Generation check failed; verify authentication and model access" }
        } finally { if (!currentCheck() && generation[providerId] == GenerationProof.CHECKING) generation.remove(providerId); probing.remove(providerId); reload() }
    }
    fun useDefault(providerId: String): Boolean {
        val row = rows.value.firstOrNull { it.descriptor.providerId == providerId } ?: return false
        if (!row.configured) return false
        router.selectProvider(providerId) // Existing router restores the per-provider model preference.
        return true
    }
}
