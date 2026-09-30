package com.halo.engines.ai

import com.halo.runtime.provider.response.AiResponseValidator
import com.halo.sdk.provider.*
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.RuntimeLogger
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.ProviderRequest
import com.halo.sdk.runtime.ProviderResult
import com.halo.sdk.runtime.ProviderFailure
import com.halo.sdk.runtime.AiRoleResolutionEvent
import com.halo.sdk.runtime.AiTargetResolvedEvent
import com.halo.sdk.runtime.RemoteProviderRequestEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import java.util.UUID

/** Routes AI requests while preserving explicit intent and provider boundaries. */
class DefaultAiProviderRouter(
    private val providerRegistry: AiProviderRegistry,
    private val storage: Storage,
    private val eventBus: EventBus? = null,
    private val logger: RuntimeLogger? = null,
    private val defaultInferenceProviderId: String? = null,
    private val defaultInferenceModelId: String? = null,
) : AiProviderRouter {
    constructor(
        providers: List<AiProvider>,
        storage: Storage,
        eventBus: EventBus? = null,
        logger: RuntimeLogger? = null,
        defaultInferenceProviderId: String? = null,
        defaultInferenceModelId: String? = null,
    ) : this(
        AiProviderRegistry.from(providers),
        storage,
        eventBus,
        logger,
        defaultInferenceProviderId,
        defaultInferenceModelId,
    )

    private val inferenceProviders: List<AiInferenceProvider> = providerRegistry.inference
    private val allProviders: List<AiProvider> = providerRegistry.all
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val selectionRepository = AiSelectionRepository(storage)

    private val _selectedProviderId = MutableStateFlow<String?>(null)
    override val selectedProviderId: StateFlow<String?> = _selectedProviderId.asStateFlow()
    private val _selectedModelId = MutableStateFlow<String?>(null)
    override val selectedModelId: StateFlow<String?> = _selectedModelId.asStateFlow()

    private val _diagnosticsFlow = MutableStateFlow<List<AiProviderDiagnostic>>(
        allProviders.map { provider ->
            AiProviderDiagnostic(provider.id, provider.displayName, ProviderStatus.State.LOADING, null, emptySet(), false, null, provider.isLocal)
        }
    )
    override val diagnosticsFlow: StateFlow<List<AiProviderDiagnostic>> = _diagnosticsFlow.asStateFlow()
    private val _routingPolicy = MutableStateFlow(AiRoutingPolicy.AUTOMATIC)
    override val routingPolicy: StateFlow<AiRoutingPolicy> = _routingPolicy.asStateFlow()

    private val _roleBindings = MutableStateFlow<Map<AiModelRole, AiRoleBinding>>(emptyMap())
    override val roleBindings: StateFlow<Map<AiModelRole, AiRoleBinding>> = _roleBindings.asStateFlow()
    
    private val roleResolver: AiRoleResolver = DefaultAiRoleResolver()

    init {
        restoreStoredRoutingState()
    }

    private fun restoreStoredRoutingState() {
        val designatedDefault = defaultInferenceProviderId
            ?.let { id -> inferenceProviders.firstOrNull { it.id == id } }
        val defaultProvider = designatedDefault?.id
            ?: inferenceProviders.firstOrNull { it.isLocal }?.id
            ?: inferenceProviders.firstOrNull()?.id
        val beforeMigration = selectionRepository.readPersistedSelection()
        val stored = selectionRepository.migrateLegacySelectionIfNeeded()
        if (stored != beforeMigration) {
            logger?.info(
                "AiProviderRouter",
                "HALO_AI_SELECTION_WRITE source=LEGACY_MIGRATION " +
                    "previousProvider=${beforeMigration.providerId} previousModel=${beforeMigration.modelId} previousPolicy=${beforeMigration.policy} " +
                    "newProvider=${stored.providerId} newModel=${stored.modelId} newPolicy=${stored.policy}",
            )
        }
        // Fresh installs and older LiteRT-only selections get one deterministic default target.
        // This is a preference, not an installation claim: if the artifact is not downloaded yet,
        // the router keeps the selection and the provider reports MODEL_MISSING instead of silently
        // switching to a remote provider.
        val resolvedProvider = stored.providerId ?: defaultProvider
        val canApplyDefaultModel = resolvedProvider == defaultProvider &&
            stored.modelId == null &&
            !defaultInferenceModelId.isNullOrBlank()
        val resolvedModel = if (canApplyDefaultModel) defaultInferenceModelId else stored.modelId
        val freshInstall = stored.providerId == null && stored.modelId == null
        val resolvedPolicy = if (
            freshInstall &&
            stored.policy == AiRoutingPolicy.AUTOMATIC &&
            resolvedProvider != null &&
            resolvedModel != null
        ) {
            AiRoutingPolicy.EXPLICIT
        } else {
            stored.policy
        }
        val resolvedSelection = PersistedAiSelection(resolvedProvider, resolvedModel, resolvedPolicy)

        _routingPolicy.value = resolvedPolicy
        _selectedProviderId.value = resolvedProvider
        _selectedModelId.value = resolvedModel
        _roleBindings.value = selectionRepository.readRoleBindings()

        // Persist the first-run/default-model choice once so process recreation cannot fall back to
        // provider ordering. Also repairs older installs that persisted LiteRT without a model.
        if (resolvedSelection != stored && (freshInstall || (stored.providerId == defaultProvider && stored.modelId == null))) {
            persistSelection(resolvedSelection, "STARTUP_DEFAULT", stored)
        }

        logger?.info(
            "AiProviderRouter",
            "HALO_AI_SELECTION_RESTORE storedProvider=${stored.providerId} " +
                "storedModel=${stored.modelId} storedPolicy=${stored.policy} " +
                "resolvedProvider=$resolvedProvider resolvedModel=$resolvedModel " +
                "fallbackReason=${when {
                    freshInstall -> "FRESH_INSTALL_DEFAULT"
                    canApplyDefaultModel -> "DEFAULT_MODEL_REPAIR"
                    else -> "NONE"
                }}",
        )
    }

    override fun start() {}

    override fun stop() = Unit

    fun setRoutingPolicy(policy: AiRoutingPolicy) {
        val previous = currentSelection()
        _routingPolicy.value = policy
        persistSelection(currentSelection(), "OTHER", previous)
    }

    override fun selectProvider(id: String) {
        if (allProviders.any { it.id == id }) {
            val previous = currentSelection()
            val providerChanged = _selectedProviderId.value != id
            _selectedProviderId.value = id
            if (providerChanged) _selectedModelId.value = null
            _routingPolicy.value = AiRoutingPolicy.EXPLICIT
            persistSelection(currentSelection(), "USER_PROVIDER", previous)
            updateDiagnosticsSelection()
            scope.launch { refreshDiagnostics() }
        }
    }

    override fun selectModel(modelId: String?) {
        val previous = currentSelection()
        _selectedModelId.value = modelId
        persistSelection(currentSelection(), "USER_MODEL", previous)
        updateDiagnosticsSelection()
        scope.launch { refreshDiagnostics() }
    }

    @Synchronized
    override fun selectTarget(ref: AiModelRef) {
        if (inferenceProviders.none { it.id == ref.providerId }) return
        val previous = currentSelection()
        _selectedProviderId.value = ref.providerId
        _selectedModelId.value = ref.modelId
        _routingPolicy.value = AiRoutingPolicy.EXPLICIT
        persistSelection(currentSelection(), "USER_TARGET", previous)
        updateDiagnosticsSelection()
        scope.launch { refreshDiagnostics() }
    }

    private fun currentSelection() = PersistedAiSelection(
        _selectedProviderId.value,
        _selectedModelId.value,
        _routingPolicy.value,
    )

    private fun persistSelection(
        selection: PersistedAiSelection,
        source: String,
        previous: PersistedAiSelection,
    ) {
        selectionRepository.writeExplicitSelection(selection)
        logger?.info(
            "AiProviderRouter",
            "HALO_AI_SELECTION_WRITE source=$source " +
                "previousProvider=${previous.providerId} previousModel=${previous.modelId} previousPolicy=${previous.policy} " +
                "newProvider=${selection.providerId} newModel=${selection.modelId} newPolicy=${selection.policy}",
        )
    }

    override fun setRoleBinding(binding: AiRoleBinding) {
        selectionRepository.writeRoleBinding(binding)
        _roleBindings.value = selectionRepository.readRoleBindings()
    }

    override fun getAllProviders(): List<AiProvider> = allProviders
    override suspend fun diagnostics(): List<AiProviderDiagnostic> = refreshDiagnostics()
    override suspend fun getEligibleProvider(request: AiRequest): AiProvider = resolveExecutionTarget(request).provider

    private val _lastRoutingDecision = MutableStateFlow<AiRoutingDecision?>(null)
    override val lastRoutingDecision: StateFlow<AiRoutingDecision?> = _lastRoutingDecision.asStateFlow()
    private val _executionDiagnostics = MutableStateFlow(AiExecutionDiagnostics())
    override val executionDiagnostics: StateFlow<AiExecutionDiagnostics> = _executionDiagnostics.asStateFlow()

    override suspend fun resolveExecutionTarget(request: AiRequest): AiExecutionTarget =
        resolveExecutionTarget(request, AiWorkloadSpec())

    override suspend fun resolveExecutionTarget(
        request: AiRequest,
        workload: AiWorkloadSpec,
    ): AiExecutionTarget {
        val pref = roleResolver.resolvePreference(
            workload,
            currentSelection(),
            _roleBindings.value,
            allProviders
        )
        val policy = pref.policy
        val selectedId = pref.providerId
        val selectedModel = pref.modelId

        val rejected = mutableListOf<RejectedAiTarget>()
        var resolved: AiExecutionTarget? = null

        val requestedRole = workload.preferredRole ?: roleResolver.determineRole(workload)
        eventBus?.publish(
            AiRoleResolutionEvent(
                workloadKind = workload.kind.name,
                requestedRole = requestedRole.name,
                resolvedRole = requestedRole.name,
                bindingMode = _roleBindings.value[requestedRole]?.selection?.name ?: "UNKNOWN",
                preferredProvider = selectedId,
                preferredModel = selectedModel
            )
        )

        try {
            if (policy == AiRoutingPolicy.EXPLICIT && selectedId == null) {
                rejected += RejectedAiTarget("none", null, TargetRejectionReason.ROLE_DISABLED)
                throw AiProviderException(
                    AiErrorType.CAPABILITY_DISABLED,
                    "${requestedRole.name} AI is disabled."
                )
            }
            val selected = allProviders.firstOrNull { it.id == selectedId }
            if (policy == AiRoutingPolicy.EXPLICIT &&
                (selected !is AiInferenceProvider || selected is AiHandoffProvider)
            ) {
                rejected += RejectedAiTarget(selectedId ?: "unconfigured", selectedModel,
                    if (selected is AiHandoffProvider) TargetRejectionReason.CAPABILITY_MISMATCH else TargetRejectionReason.CONFIGURATION_MISSING)
                throw AiProviderException(
                    if (selected is AiHandoffProvider) AiErrorType.UNSUPPORTED_CAPABILITY else AiErrorType.PROVIDER_UNAVAILABLE,
                    "Selected target cannot execute inference",
                )
            }
            val candidates = if (policy == AiRoutingPolicy.EXPLICIT) listOfNotNull(selected as? AiInferenceProvider)
                else inferenceProviders.sortedWith(
                    compareBy<AiInferenceProvider> { if (it.id == selectedId) 0 else if (it.isLocal) 1 else 2 }
                        .thenBy { it.id }
                )
            for (provider in candidates) {
                if ((policy == AiRoutingPolicy.LOCAL_ONLY ||
                        (currentSelection().policy == AiRoutingPolicy.LOCAL_ONLY && AiCapability.IMAGE in workload.requiredCapabilities)) && !provider.isLocal) {
                    rejected += RejectedAiTarget(provider.id, null, TargetRejectionReason.POLICY_EXCLUDED)
                    continue
                }
                if (!workload.allowRemote && !provider.isLocal) {
                    rejected += RejectedAiTarget(
                        provider.id,
                        selectedModel.takeIf { provider.id == selectedId },
                        TargetRejectionReason.REMOTE_CONTEXT_FORBIDDEN
                    )
                    if (policy == AiRoutingPolicy.EXPLICIT) {
                        throw AiProviderException(
                            AiErrorType.UNSUPPORTED_CAPABILITY,
                            "Required context is not permitted to be sent to the selected remote provider",
                            AiFailureDetails(recoverability = Recoverability.USER_ACTION_REQUIRED),
                        )
                    }
                    continue
                }
                var readinessReason: TargetRejectionReason? = null
                try {
                    val status = provider.getStatus()
                    readinessReason = status.rejectionReason
                    status.cooldown?.takeIf { it.isActive() }?.let { cooldown ->
                        throw AiProviderException(
                            type = cooldown.reason,
                            message = status.message ?: "Provider is temporarily cooling down",
                            details = AiFailureDetails(
                                retryAfterMs = cooldown.remainingMs(),
                                recoverability = Recoverability.RETRY_AFTER,
                            ),
                        )
                    }
                    val ready = status.state == ProviderStatus.State.READY ||
                        status.readiness is ProviderReadiness.ConfiguredCold ||
                        status.readiness is ProviderReadiness.Ready ||
                        status.readiness is ProviderReadiness.Loading
                    if (!ready) {
                        val type = when (status.state) {
                            ProviderStatus.State.UNCONFIGURED -> AiErrorType.AUTHENTICATION_FAILED
                            ProviderStatus.State.MODEL_MISSING -> AiErrorType.LOCAL_MODEL_UNAVAILABLE
                            ProviderStatus.State.OFFLINE -> AiErrorType.NETWORK_UNAVAILABLE
                            else -> AiErrorType.PROVIDER_UNAVAILABLE
                        }
                        throw AiProviderException(type, "Provider is not ready")
                    }
                    val explicitModelId = request.modelId
                        ?: selectedModel.takeIf {
                            policy == AiRoutingPolicy.EXPLICIT && provider.id == selectedId
                        }
                    resolved = resolveTargetForProvider(
                        provider = provider,
                        request = request.copy(modelId = explicitModelId),
                        workload = workload,
                        preferredModelId = selectedModel.takeIf { provider.id == selectedId },
                        allowPreferredModelFallback = policy != AiRoutingPolicy.EXPLICIT,
                    )
                    eventBus?.publish(AiTargetResolvedEvent(
                resolvedProvider = resolved!!.providerId,
                resolvedModel = resolved.modelId,
                isLocal = resolved.provider.isLocal,
                fallbackUsed = policy != AiRoutingPolicy.EXPLICIT && (resolved.providerId != selectedId || resolved.modelId != selectedModel)
            ))
            return resolved
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    rejected += RejectedAiTarget(provider.id, request.modelId ?: selectedModel.takeIf { provider.id == selectedId }, readinessReason ?: rejectionReason(error))
                    if (policy == AiRoutingPolicy.EXPLICIT) throw error
                }
            }
            throw AiProviderException(AiErrorType.PROVIDER_UNAVAILABLE, "No eligible provider/model target is ready for workload ${workload.kind}")
        } finally {
            _lastRoutingDecision.value = AiRoutingDecision(policy, selectedId, selectedModel,
                resolved?.providerId, resolved?.modelId, rejected.toList())
        }
    }

    private fun rejectionReason(error: Exception): TargetRejectionReason = when ((error as? AiProviderException)?.type) {
        AiErrorType.MODEL_NOT_FOUND, AiErrorType.MODEL_UNAVAILABLE -> TargetRejectionReason.MODEL_NOT_ADVERTISED
        AiErrorType.LOCAL_MODEL_UNAVAILABLE -> TargetRejectionReason.MODEL_NOT_INSTALLED
        AiErrorType.UNSUPPORTED_CAPABILITY, AiErrorType.INVALID_REQUEST -> TargetRejectionReason.CAPABILITY_MISMATCH
        AiErrorType.CONTEXT_TOO_LARGE -> TargetRejectionReason.MEMORY_PREFLIGHT_FAILED
        AiErrorType.NETWORK_ERROR, AiErrorType.NETWORK_UNAVAILABLE -> TargetRejectionReason.NETWORK_UNAVAILABLE
        AiErrorType.AUTHENTICATION_FAILED, AiErrorType.MODEL_NOT_SELECTED -> TargetRejectionReason.CONFIGURATION_MISSING
        else -> TargetRejectionReason.PROVIDER_NOT_READY
    }

    private fun updateDiagnosticsSelection() {
        val selectedId = _selectedProviderId.value
        _diagnosticsFlow.value = _diagnosticsFlow.value.map { diag ->
            diag.copy(
                selected = diag.providerId == selectedId,
                selectedModelId = if (diag.providerId == selectedId) _selectedModelId.value else null
            )
        }
    }

    private suspend fun refreshDiagnostics(): List<AiProviderDiagnostic> = withContext(Dispatchers.IO) {
        val selectedId = _selectedProviderId.value
        val list = allProviders.map { provider ->
            val status = runCatching { provider.getStatus() }.getOrElse {
                ProviderStatus(
                    ProviderStatus.State.ERROR,
                    it.message ?: "Status check failed"
                )
            }
            val capabilities = runCatching { provider.getCapabilities() }.getOrDefault(emptySet())
            AiProviderDiagnostic(
                provider.id,
                provider.displayName,
                status.state,
                status.message,
                capabilities,
                provider.id == selectedId,
                if (provider.id == selectedId) _selectedModelId.value else null,
                provider.isLocal
            )
        }
        _diagnosticsFlow.value = list
        list
    }

    private suspend fun resolveTargetForProvider(
        provider: AiInferenceProvider,
        request: AiRequest,
        workload: AiWorkloadSpec,
        preferredModelId: String? = null,
        allowPreferredModelFallback: Boolean = true,
    ): AiExecutionTarget {
        val providerCapabilities = provider.getCapabilities()
        request.validateAgainst(providerCapabilities)
        val models = provider.listModels()
        if (models.isEmpty()) {
            throw AiProviderException(
                AiErrorType.MODEL_UNAVAILABLE,
                "Provider ${provider.id} did not advertise any models"
            )
        }

        val candidateModel = request.modelId?.let { explicitId ->
            val explicit = models.firstOrNull { it.id == explicitId }
                ?: throw AiProviderException(
                    AiErrorType.MODEL_NOT_FOUND,
                    "Model $explicitId is not advertised by provider ${provider.id}"
                )
            validateModelWorkload(explicit, request, workload, providerCapabilities)
            explicit
        } ?: preferredModelId?.let { preferredId ->
            val preferred = models.firstOrNull { it.id == preferredId }
            when {
                preferred == null && !allowPreferredModelFallback ->
                    throw AiProviderException(
                        AiErrorType.MODEL_NOT_FOUND,
                        "Selected model $preferredId is not advertised by provider ${provider.id}"
                    )
                preferred != null && supportsModelWorkload(preferred, request, workload, providerCapabilities) ->
                    preferred
                preferred != null && !allowPreferredModelFallback -> {
                    validateModelWorkload(preferred, request, workload, providerCapabilities)
                    preferred
                }
                else -> null
            }
        } ?: models.firstOrNull {
            supportsModelWorkload(it, request, workload, providerCapabilities)
        } ?: throw AiProviderException(
            AiErrorType.UNSUPPORTED_CAPABILITY,
            "Provider ${provider.id} has no advertised model compatible with workload ${workload.kind}"
        )

        validateModelWorkload(candidateModel, request, workload, providerCapabilities)
        val targetCapabilities = (provider as? ModelInputCapabilityReporting)?.capabilitiesForModel(candidateModel.id) ?: providerCapabilities
        validateModelWorkload(candidateModel, request, workload, targetCapabilities)

        return AiExecutionTarget(
            provider = provider,
            model = candidateModel,
            providerCapabilities = targetCapabilities,
        )
    }

    private fun supportsModelWorkload(
        model: AiModel,
        request: AiRequest,
        workload: AiWorkloadSpec,
        providerCapabilities: Set<AiCapability>,
    ): Boolean {
        if (!workload.allowRemote && model.executionLocation != AiExecutionLocation.ON_DEVICE) return false
        if (currentSelection().policy == AiRoutingPolicy.LOCAL_ONLY && AiCapability.IMAGE in workload.requiredCapabilities && model.executionLocation != AiExecutionLocation.ON_DEVICE) return false
        val effectiveCaps = providerCapabilities.intersect(
            model.inputCapabilities +
                (if (model.supportsTools && AiCapability.TOOLS in providerCapabilities) {
                    setOf(AiCapability.TOOLS)
                } else emptySet()) +
                (if (model.supportsStreaming && AiCapability.STREAMING in providerCapabilities) {
                    setOf(AiCapability.STREAMING)
                } else emptySet())
        )

        if (workload.requiredCapabilities.any { it !in effectiveCaps }) return false
        if (workload.tools == AiFeatureRequirement.REQUIRED &&
            AiCapability.TOOLS !in effectiveCaps
        ) return false
        if (workload.streaming == AiFeatureRequirement.REQUIRED &&
            AiCapability.STREAMING !in effectiveCaps
        ) return false
        val contextLength = model.contextLength
        if (workload.minimumContextTokens > 0 &&
            (contextLength == null || contextLength < workload.minimumContextTokens)
        ) return false

        return (request.text.isNullOrBlank() || AiCapability.TEXT in effectiveCaps) &&
            (request.image == null || AiCapability.IMAGE in effectiveCaps) &&
            (request.audio == null || AiCapability.AUDIO in effectiveCaps) &&
            (request.video == null || AiCapability.VIDEO in effectiveCaps)
    }

    private fun validateModelWorkload(
        model: AiModel,
        request: AiRequest,
        workload: AiWorkloadSpec,
        providerCapabilities: Set<AiCapability>,
    ) {
        if (!supportsModelWorkload(model, request, workload, providerCapabilities)) {
            throw AiProviderException(
                AiErrorType.UNSUPPORTED_CAPABILITY,
                "Model ${model.id} does not support workload ${workload.kind}"
            )
        }
    }

    override suspend fun resolveFallbackExecutionTarget(
        request: AiRequest,
        workload: AiWorkloadSpec,
        failedProviderId: String,
    ): AiExecutionTarget? {
        val policy = _routingPolicy.value
        if (policy == AiRoutingPolicy.EXPLICIT) return null
        val localOnly = policy == AiRoutingPolicy.LOCAL_ONLY
        // The failed target's model id was bound during preparation. It is not meaningful in a
        // different provider namespace and would make every cross-provider fallback look missing.
        val fallbackRequest = request.copy(modelId = null)
        for (provider in inferenceProviders.filter { it.id != failedProviderId }) {
            if (localOnly && !provider.isLocal) continue
            if (!workload.allowRemote && !provider.isLocal) continue
            val status = runCatching { provider.getStatus() }.getOrNull()
            val isReady = status?.state == ProviderStatus.State.READY || status?.readiness is ProviderReadiness.ConfiguredCold || status?.readiness is ProviderReadiness.Ready
            if (!isReady) continue
            val target = runCatching { resolveTargetForProvider(
                    provider = provider,
                    request = fallbackRequest,
                    workload = workload,
                    preferredModelId = null,
                    allowPreferredModelFallback = true,
                ) }.getOrNull()
            if (target != null) {
                _lastRoutingDecision.value = _lastRoutingDecision.value?.copy(resolvedProviderId = target.providerId, resolvedModelId = target.modelId)
                return target
            }
        }
        return null
    }

    private fun fallbackEligible(error: Throwable): Boolean = error is AiProviderException && error.type in setOf(
        AiErrorType.PROVIDER_UNAVAILABLE, AiErrorType.MODEL_UNAVAILABLE, AiErrorType.MODEL_LOAD_FAILED,
        AiErrorType.MODEL_NOT_READY, AiErrorType.GENERATION_FAILED, AiErrorType.EMPTY_RESPONSE,
        AiErrorType.TIMEOUT, AiErrorType.NETWORK_UNAVAILABLE, AiErrorType.NETWORK_ERROR,
        AiErrorType.LOCAL_MODEL_UNAVAILABLE, AiErrorType.UNKNOWN_ERROR
    )

    override suspend fun execute(request: AiRequest, context: AiContext): AiResponse =
        execute(request, AiWorkloadSpec(), context)

    override suspend fun execute(request: AiRequest, workload: AiWorkloadSpec, context: AiContext): AiResponse {
        val policy = _routingPolicy.value
        val target = resolveExecutionTarget(request, workload)
        val finalRequest = request.copy(modelId = target.modelId)
        val requestId = UUID.randomUUID().toString()
        val startedAtMs = System.currentTimeMillis()
        _executionDiagnostics.value = AiExecutionDiagnostics(
            inFlight = true,
            providerId = target.providerId,
            modelId = target.modelId,
            startedAtMs = startedAtMs,
            diagnosticsId = requestId,
        )
        logger?.info("AiProviderRouter", "AI execute start provider=${target.providerId} model=${target.modelId}")
        eventBus?.publish(ProviderRequest(requestId = requestId, providerId = target.providerId))
        
        if (!target.provider.isLocal) {
            eventBus?.publish(RemoteProviderRequestEvent(
                interactionId = request.modelId, // Hack since request doesn't have interactionId but we can't easily get it here without breaking api
                requestId = requestId,
                providerId = target.providerId,
                modelId = target.modelId,
                routingPolicy = policy.name,
                role = "UNKNOWN", // Don't have requested role easily here
                reason = "Remote execution requested"
            ))
        }

        return try {
            finalRequest.validateAgainst(target.providerCapabilities)
            validateModelWorkload(target.model, finalRequest, workload, target.providerCapabilities)
            val response = target.provider.generate(finalRequest, context)
            val validated = AiResponseValidator.validate(target.provider, target.effectiveCapabilities, target.modelId, response)
            eventBus?.publish(ProviderResult(requestId = requestId, success = true))
            recordExecutionSuccess(target, validated, startedAtMs, requestId)
            validated
        } catch (e: CancellationException) {
            recordExecutionFailure(target, e, "CANCEL", startedAtMs, requestId)
            throw e
        }
        catch (e: Throwable) {
            eventBus?.publish(ProviderFailure(requestId = requestId, providerId = target.providerId, reason = e.message ?: "Execution failed"))
            if ((policy == AiRoutingPolicy.PREFERRED_WITH_FALLBACK || policy == AiRoutingPolicy.AUTOMATIC || policy == AiRoutingPolicy.LOCAL_ONLY) && fallbackEligible(e)) {
                val fallbackTarget = resolveFallbackExecutionTarget(request, workload, failedProviderId = target.providerId)
                if (fallbackTarget != null) {
                    val fallbackRequest = request.copy(modelId = fallbackTarget.modelId)
                    val fallbackRequestId = UUID.randomUUID().toString()
                    eventBus?.publish(ProviderRequest(requestId = fallbackRequestId, providerId = fallbackTarget.providerId))
                    try {
                        fallbackRequest.validateAgainst(fallbackTarget.providerCapabilities)
                        validateModelWorkload(fallbackTarget.model, fallbackRequest, workload, fallbackTarget.providerCapabilities)
                        val fallbackResponse = fallbackTarget.provider.generate(fallbackRequest, context)
                        val validatedFallback = AiResponseValidator.validate(fallbackTarget.provider, fallbackTarget.effectiveCapabilities, fallbackTarget.modelId, fallbackResponse)
                        eventBus?.publish(ProviderResult(requestId = fallbackRequestId, success = true))
                        recordExecutionSuccess(fallbackTarget, validatedFallback, startedAtMs, fallbackRequestId)
                        logger?.info("AiProviderRouter", "AI fallback success provider=${fallbackTarget.providerId} model=${fallbackTarget.modelId}")
                        return validatedFallback
                    } catch (fallbackError: CancellationException) {
                        recordExecutionFailure(fallbackTarget, fallbackError, "CANCEL", startedAtMs, fallbackRequestId)
                        throw fallbackError
                    }
                    catch (fallbackError: Throwable) {
                        eventBus?.publish(ProviderFailure(requestId = fallbackRequestId, providerId = fallbackTarget.providerId, reason = fallbackError.message ?: "Fallback execution failed"))
                        // Preserve both failures for diagnostics while keeping the primary target's
                        // error as the stable outward contract.
                        if (fallbackError !== e) e.addSuppressed(fallbackError)
                    }
                }
            }
            recordExecutionFailure(target, e, "GENERATION", startedAtMs, requestId)
            throw e
        }
    }

    private fun recordExecutionSuccess(
        target: AiExecutionTarget,
        response: AiResponse,
        startedAtMs: Long,
        diagnosticsId: String,
    ) {
        val completedAtMs = System.currentTimeMillis()
        _executionDiagnostics.value = AiExecutionDiagnostics(
            inFlight = false,
            providerId = target.providerId,
            modelId = target.modelId,
            startedAtMs = startedAtMs,
            lastSuccessAtMs = completedAtMs,
            lastLatencyMs = response.metrics?.totalDurationMs ?: (completedAtMs - startedAtMs),
            lastBackend = response.executionBackend,
            diagnosticsId = diagnosticsId,
            recordedAtMs = completedAtMs,
        )
    }

    private fun recordExecutionFailure(
        target: AiExecutionTarget,
        error: Throwable,
        stage: String,
        startedAtMs: Long,
        diagnosticsId: String,
    ) {
        val completedAtMs = System.currentTimeMillis()
        _executionDiagnostics.value = AiExecutionDiagnostics(
            inFlight = false,
            providerId = target.providerId,
            modelId = target.modelId,
            startedAtMs = startedAtMs,
            lastLatencyMs = completedAtMs - startedAtMs,
            lastFailureStage = stage,
            lastFailureType = error::class.java.simpleName,
            lastFailureMessage = error.message?.take(240),
            diagnosticsId = diagnosticsId,
            recordedAtMs = completedAtMs,
        )
    }

    companion object {
        /** Live on-device provider. The only local provider ID new code may reference. */
        const val LITERT_PROVIDER_ID = "provider.litert.local"

        /** Retired llama.cpp provider ID. Kept for reading (and rewriting) persisted config only. */
        const val LEGACY_LLAMACPP_PROVIDER_ID = "provider.llamacpp.native"
    }
}
