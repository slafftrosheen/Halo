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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
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
    private val responseLanguage: () -> String? = { null },
    private val qualifiedAutomaticTarget: (AiWorkloadSpec)->AiModelRef? = { null },
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
    private val configurationCounter = java.util.concurrent.atomic.AtomicLong()
    override val configurationRevision: Long get() = configurationCounter.get()

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
    private val eligibility = AiTargetEligibilityEvaluator()
    private data class CandidateCounts(var total: Int = 0, var rejected: Int = 0)

    override suspend fun candidatesForRole(role: AiModelRole, catalogue: List<AiModelCatalogEntry>,
        configuredProviderIds: Set<String>): List<AiRoleCandidate> {
        return catalogue.map { entry ->
            val provider = inferenceProviders.firstOrNull { it.id == entry.model.providerId }
            val reasons = mutableSetOf<AiRoleCandidateRejection>()
            if (provider == null || provider.id !in configuredProviderIds) reasons += AiRoleCandidateRejection.PROVIDER_NOT_CONFIGURED
            if (entry.runtime.installed == false) reasons += AiRoleCandidateRejection.MODEL_UNAVAILABLE
            val snapshot = try {
                provider?.let { eligibility.capabilities(it, entry.model) }
                    ?: AiTargetCapabilitySnapshot(emptySet(), ToolCallProtocol.NONE)
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { reasons += AiRoleCandidateRejection.MODEL_UNAVAILABLE; AiTargetCapabilitySnapshot(emptySet(), ToolCallProtocol.NONE) }
            val model = entry.model
            val candidate = eligibility.evaluate(model, snapshot, AiRequest(), AiRoleRequirements.workload(role), _routingPolicy.value)
            candidate.copy(eligible = candidate.eligible && reasons.isEmpty(), rejectionReasons = candidate.rejectionReasons + reasons)
        }.sortedWith(AiRoleCandidateRanking.comparator(role, readyDefault()))
    }

    private fun readyDefault(): AiModelRef? {
        val provider = inferenceProviders.firstOrNull { it.id == selectedProviderId.value } ?: return null
        val model = selectedModelId.value ?: return null
        val ready = if (provider is LocalInferenceExecutionReporting) provider.localExecutionIdentity(model) != null
            else provider.status.value.readiness is ProviderReadiness.Ready || provider.status.value.state == ProviderStatus.State.READY
        return AiModelRef(provider.id, model).takeIf { ready }
    }

    private suspend fun rankTargets(targets: List<AiExecutionTarget>, role: AiModelRole): List<AiExecutionTarget> {
        val evidence = targets.associateWith {
            val protocol = it.toolProtocol ?: eligibility.capabilities(it.provider, it.model).toolProtocol
            AiRoleCandidate(AiModelRef(it.providerId, it.modelId), role, true, it.effectiveCapabilities,
                it.model.executionLocation, toolProtocol = protocol)
        }
        val order = AiRoleCandidateRanking.comparator(role, readyDefault())
        return targets.sortedWith { a, b -> order.compare(evidence.getValue(a), evidence.getValue(b)) }
    }

    private suspend fun logRoleTarget(request: AiRequest, workload: AiWorkloadSpec, target: AiExecutionTarget,
        fallback: Boolean, candidates: Int, rejected: Int) {
        val role = roleResolver.determineRole(workload)
        val mode = if (role == AiModelRole.CHAT) "CONVERSATION_DEFAULT" else roleBinding(role).selection.name
        val protocol = target.toolProtocol ?: eligibility.capabilities(target.provider, target.model).toolProtocol
        eventBus?.publish(AiTargetResolvedEvent(resolvedProvider = target.providerId, resolvedModel = target.modelId, isLocal = target.provider.isLocal, fallbackUsed = fallback))
        logger?.info("AiProviderRouter", "HALO_ROLE_TARGET_RESOLVED interactionId=${request.interactionId} role=$role bindingMode=$mode provider=${target.providerId} model=${target.modelId} location=${target.model.executionLocation} toolProtocol=$protocol fallbackUsed=$fallback candidateCount=$candidates rejectedCount=$rejected")
    }

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

    @Synchronized
    fun setRoutingPolicy(policy: AiRoutingPolicy) {
        val previous = currentSelection()
        _routingPolicy.value = policy
        persistSelection(currentSelection(), "OTHER", previous)
    }

    @Synchronized
    override fun selectProvider(id: String) {
        if (allProviders.any { it.id == id }) {
            val previous = currentSelection()
            val providerChanged = _selectedProviderId.value != id
            _selectedProviderId.value = id
            if (providerChanged) {
                previous.providerId?.let { provider -> previous.modelId?.let { storage.putString("ai_last_model_$provider", it) } }
                _selectedModelId.value = storage.getString("ai_last_model_$id")
            }
            _routingPolicy.value = AiRoutingPolicy.EXPLICIT
            persistSelection(currentSelection(), "USER_PROVIDER", previous)
            updateDiagnosticsSelection()
            scope.launch { refreshDiagnostics() }
        }
    }

    @Synchronized
    override fun selectModel(modelId: String?) {
        val previous = currentSelection()
        _selectedModelId.value = modelId
        _selectedProviderId.value?.let { provider -> if (modelId == null) storage.remove("ai_last_model_$provider") else storage.putString("ai_last_model_$provider", modelId) }
        persistSelection(currentSelection(), "USER_MODEL", previous)
        updateDiagnosticsSelection()
        scope.launch { refreshDiagnostics() }
    }

    @Synchronized
    override fun selectTarget(ref: AiModelRef) {
        if (inferenceProviders.none { it.id == ref.providerId }) return
        val previous = currentSelection()
        previous.providerId?.let { provider -> previous.modelId?.let { storage.putString("ai_last_model_$provider", it) } }
        storage.putString("ai_last_model_${ref.providerId}", ref.modelId)
        _selectedProviderId.value = ref.providerId
        _selectedModelId.value = ref.modelId
        _routingPolicy.value = AiRoutingPolicy.EXPLICIT
        persistSelection(currentSelection(), "USER_TARGET", previous)
        updateDiagnosticsSelection()
        scope.launch { refreshDiagnostics() }
    }

    @Synchronized
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
        configurationCounter.incrementAndGet()
        selectionRepository.writeExplicitSelection(selection)
        logger?.info(
            "AiProviderRouter",
            "HALO_AI_SELECTION_WRITE source=$source " +
                "previousProvider=${previous.providerId} previousModel=${previous.modelId} previousPolicy=${previous.policy} " +
                "newProvider=${selection.providerId} newModel=${selection.modelId} newPolicy=${selection.policy}",
        )
    }

    @Synchronized
    override fun setRoleBinding(binding: AiRoleBinding) {
        configurationCounter.incrementAndGet()
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
        workload.pinnedTarget?.let { ref ->
            val target = resolvePinnedExecutionTarget(request.copy(modelId = ref.modelId), workload,
                ref.providerId, ref.modelId)
            _lastRoutingDecision.value = AiRoutingDecision(AiRoutingPolicy.EXPLICIT,
                ref.providerId, ref.modelId, target.providerId, target.modelId, emptyList())
            logRoleTarget(request, workload, target, false, 1, 0)
            return target
        }
        val configuredPreference = roleResolver.resolvePreference(
            workload,
            currentSelection(),
            _roleBindings.value,
            allProviders
        )
        val automaticRole=roleResolver.determineRole(workload)
        val measured=if(automaticRole!=AiModelRole.CHAT && roleBinding(automaticRole).selection==AiRoleSelection.AUTOMATIC)
            qualifiedAutomaticTarget(workload) else null
        // Read-only decision for this turn; never persist or replace an explicit assignment.
        val pref=measured?.let { AiExecutionPreference(it.providerId,it.modelId,AiRoutingPolicy.EXPLICIT) } ?: configuredPreference
        val policy = pref.policy
        val selectedId = pref.providerId
        val selectedModel = pref.modelId

        val rejected = mutableListOf<RejectedAiTarget>()
        var resolved: AiExecutionTarget? = null

        val requestedRole = roleResolver.determineRole(workload)
        val eligibleTargets = mutableListOf<AiExecutionTarget>()
        val counts = CandidateCounts()
        eventBus?.publish(
            AiRoleResolutionEvent(
                workloadKind = workload.kind.name,
                requestedRole = requestedRole.name,
                resolvedRole = requestedRole.name,
                bindingMode = if (requestedRole == AiModelRole.CHAT) "CONVERSATION_DEFAULT" else roleBinding(requestedRole).selection.name,
                preferredProvider = selectedId,
                preferredModel = selectedModel
            )
        )

        try {
            if (requestedRole != AiModelRole.CHAT && roleBinding(requestedRole).selection == AiRoleSelection.EXPLICIT &&
                !roleBinding(requestedRole).hasExplicitTarget) {
                throw AiProviderException(AiErrorType.MODEL_UNAVAILABLE, "Configured model unavailable. Choose another model or use automatic routing.")
            }
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
                        (currentSelection().policy == AiRoutingPolicy.LOCAL_ONLY)) && !provider.isLocal) {
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
                    val explicitModelId = if (requestedRole != AiModelRole.CHAT && roleBinding(requestedRole).selection == AiRoleSelection.EXPLICIT)
                        selectedModel else request.modelId ?: selectedModel.takeIf {
                            policy == AiRoutingPolicy.EXPLICIT && provider.id == selectedId
                        }
                    // Local readiness must follow the routed artifact, not the conversation default.
                    val exactTarget = if (provider is ModelReadinessReporting) resolveTargetForProvider(
                        provider, request.copy(modelId = explicitModelId), workload,
                        selectedModel.takeIf { provider.id == selectedId }, policy != AiRoutingPolicy.EXPLICIT, counts,
                    ) else null
                    val status = if (exactTarget != null && provider is ModelReadinessReporting)
                        provider.statusForModel(exactTarget.modelId) else provider.getStatus()
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
                    resolved = exactTarget ?: resolveTargetForProvider(
                        provider = provider,
                        request = request.copy(modelId = explicitModelId),
                        workload = workload,
                        preferredModelId = selectedModel.takeIf { provider.id == selectedId },
                        allowPreferredModelFallback = policy != AiRoutingPolicy.EXPLICIT,
                        counts = counts,
                    )
                    if (policy != AiRoutingPolicy.AUTOMATIC || requestedRole == AiModelRole.CHAT || roleBinding(requestedRole).selection != AiRoleSelection.AUTOMATIC) {
                        logRoleTarget(request, workload, resolved, false, counts.total, counts.rejected)
                        return resolved
                    }
                    eligibleTargets += resolved
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    rejected += RejectedAiTarget(provider.id, request.modelId ?: selectedModel.takeIf { provider.id == selectedId }, readinessReason ?: rejectionReason(error))
                    if (policy == AiRoutingPolicy.EXPLICIT) throw error
                }
            }
            if (eligibleTargets.isNotEmpty()) {
                resolved = rankTargets(eligibleTargets, requestedRole).first()
                logRoleTarget(request, workload, resolved, false, counts.total, counts.rejected)
                return resolved
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
            val status = try { provider.getStatus() } catch (cancelled: CancellationException) { throw cancelled } catch (error: Exception) {
                ProviderStatus(
                    ProviderStatus.State.ERROR,
                    error.message ?: "Status check failed"
                )
            }
            val capabilities = try { provider.getCapabilities() } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { emptySet() }
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
        counts: CandidateCounts = CandidateCounts(),
    ): AiExecutionTarget {
        val models = provider.listModels()
        if (models.isEmpty()) throw AiProviderException(AiErrorType.MODEL_UNAVAILABLE, "Provider did not advertise any models")
        val exactId = request.modelId ?: preferredModelId.takeIf { !allowPreferredModelFallback }
        val targets = mutableListOf<AiExecutionTarget>()
        for (model in models) {
            if (exactId != null && model.id != exactId) continue
            counts.total++
            val snapshot = eligibility.capabilities(provider, model)
            val evaluation = eligibility.evaluate(model, snapshot, request, workload, currentSelection().policy)
            if (evaluation.eligible) targets += AiExecutionTarget(provider, model, snapshot.effectiveCapabilities, snapshot.effectiveCapabilities, snapshot.toolProtocol)
            else {
                counts.rejected++
                if (exactId != null) throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY,
                    "Configured model incompatible: ${evaluation.rejectionReasons.joinToString()}")
            }
        }
        if (exactId != null && models.none { it.id == exactId })
            throw AiProviderException(AiErrorType.MODEL_NOT_FOUND, "Configured model unavailable")
        if (targets.isEmpty()) throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "No compatible model for this workload")
        return targets.firstOrNull { it.modelId == preferredModelId }
            ?: rankTargets(targets, roleResolver.determineRole(workload)).first()
    }

    private suspend fun validateModelWorkload(target: AiExecutionTarget, request: AiRequest, workload: AiWorkloadSpec) {
        val protocol = target.toolProtocol ?: (target.provider as? ToolProtocolReporting)?.toolCallProtocol(target.modelId)
            ?: if (AiCapability.TOOLS in target.effectiveCapabilities) ToolCallProtocol.NATIVE else ToolCallProtocol.NONE
        if (!eligibility.evaluate(target.model, AiTargetCapabilitySnapshot(target.effectiveCapabilities, protocol),
                request, workload, currentSelection().policy).eligible)
            throw AiProviderException(AiErrorType.UNSUPPORTED_CAPABILITY, "Target no longer satisfies the workload")
    }

    override suspend fun resolveFallbackExecutionTarget(
        request: AiRequest,
        workload: AiWorkloadSpec,
        failedProviderId: String,
    ): AiExecutionTarget? {
        if (workload.pinnedTarget != null) return null
        val policy = roleResolver.resolvePreference(workload, currentSelection(), _roleBindings.value, allProviders).policy
        if (policy == AiRoutingPolicy.EXPLICIT || !workload.allowFallback) return null
        val localOnly = policy == AiRoutingPolicy.LOCAL_ONLY || _routingPolicy.value == AiRoutingPolicy.LOCAL_ONLY
        // The failed target's model id was bound during preparation. It is not meaningful in a
        // different provider namespace and would make every cross-provider fallback look missing.
        val fallbackRequest = request.copy(modelId = null)
        val fallbackTargets = mutableListOf<AiExecutionTarget>()
        val counts = CandidateCounts()
        for (provider in inferenceProviders.filter { it.id != failedProviderId }) {
            if (localOnly && !provider.isLocal) continue
            if (!workload.allowRemote && !provider.isLocal) continue
            val exactTarget = if (provider is ModelReadinessReporting) try {
                resolveTargetForProvider(provider, fallbackRequest, workload, counts = counts)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null } else null
            if (provider is ModelReadinessReporting && exactTarget == null) continue
            val status = try {
                if (provider is ModelReadinessReporting && exactTarget != null) provider.statusForModel(exactTarget.modelId)
                else provider.getStatus()
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            val isReady = status?.state == ProviderStatus.State.READY || status?.readiness is ProviderReadiness.ConfiguredCold || status?.readiness is ProviderReadiness.Ready
            if (!isReady || status?.cooldown?.isActive() == true) continue
            val target = exactTarget ?: try {
                resolveTargetForProvider(provider, fallbackRequest, workload, counts = counts)
            } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { null }
            if (target != null) {
                fallbackTargets += target
            }
        }
        return rankTargets(fallbackTargets, roleResolver.determineRole(workload)).firstOrNull()?.also {
            _lastRoutingDecision.value = _lastRoutingDecision.value?.copy(resolvedProviderId = it.providerId, resolvedModelId = it.modelId)
            logRoleTarget(request, workload, it, true, counts.total, counts.rejected)
        }
    }

    private fun fallbackEligible(error: Throwable): Boolean = error is AiProviderException &&
        error.details.providerCode != "STREAM_FAILED_AFTER_OUTPUT" && error.type in setOf(
        AiErrorType.PROVIDER_UNAVAILABLE, AiErrorType.MODEL_UNAVAILABLE, AiErrorType.MODEL_LOAD_FAILED,
        AiErrorType.MODEL_NOT_READY, AiErrorType.GENERATION_FAILED, AiErrorType.EMPTY_RESPONSE,
        AiErrorType.TIMEOUT, AiErrorType.NETWORK_UNAVAILABLE, AiErrorType.NETWORK_ERROR,
        AiErrorType.LOCAL_MODEL_UNAVAILABLE, AiErrorType.UNKNOWN_ERROR
    )

    override suspend fun execute(request: AiRequest, context: AiContext): AiResponse =
        execute(request, AiWorkloadSpec(), context)

    /** Standalone text assistance uses the same remote transport as conversational turns. */
    private suspend fun generateStandalone(target: AiExecutionTarget, request: AiRequest, context: AiContext): AiResponse {
        if (target.provider.isLocal || request.toolUseMode != ToolUseMode.NONE ||
            AiCapability.STREAMING !in target.providerCapabilities) {
            return target.provider.generate(request, context)
        }
        var completed: AiResponse? = null
        var outputObserved = false
        try {
            target.provider.stream(request, context.copy(availableTools = emptyList())).collect { event ->
                when (event) {
                    is AiStreamEvent.Token -> if (event.text.isNotEmpty()) outputObserved = true
                    is AiStreamEvent.ToolCallDelta -> {
                        outputObserved = true
                        throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Tool-free assistance returned a tool call")
                    }
                    is AiStreamEvent.Completed -> {
                        if (completed != null) throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Provider completed twice")
                        outputObserved = outputObserved || event.response.text.isNotEmpty() || event.response.toolCalls.isNotEmpty()
                        completed = event.response
                    }
                    is AiStreamEvent.Failed -> throw event.error
                    else -> Unit
                }
            }
            val response = completed ?: throw AiProviderException(AiErrorType.INVALID_RESPONSE, "Provider stream ended without a completed response")
            // Validation must remain inside the partial-output guard as well.
            return AiResponseValidator.validate(target.provider, target.effectiveCapabilities, target.modelId, response)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            if (outputObserved) throw AiProviderException(
                (error as? AiProviderException)?.type ?: AiErrorType.INVALID_RESPONSE,
                "Provider stream failed after output", cause = error,
                details = ((error as? AiProviderException)?.details ?: AiFailureDetails()).copy(
                    providerCode = "STREAM_FAILED_AFTER_OUTPUT", recoverability = Recoverability.PERMANENT))
            throw error
        }
    }

    override suspend fun execute(request: AiRequest, workload: AiWorkloadSpec, context: AiContext): AiResponse {
        val localizedContext = context.withResponseLanguage(responseLanguage())
        val policy = if (workload.pinnedTarget != null) AiRoutingPolicy.EXPLICIT else
            roleResolver.resolvePreference(workload, currentSelection(), _roleBindings.value, allProviders).policy
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
        return try {
        eventBus?.publish(ProviderRequest(requestId = requestId, providerId = target.providerId))
        
        if (!target.provider.isLocal) {
            eventBus?.publish(RemoteProviderRequestEvent(
                interactionId = request.interactionId,
                requestId = requestId,
                providerId = target.providerId,
                modelId = target.modelId,
                routingPolicy = policy.name,
                role = roleResolver.determineRole(workload).name,
                reason = "Remote execution requested"
            ))
        }

            finalRequest.validateAgainst(target.providerCapabilities)
            validateModelWorkload(target, finalRequest, workload)
            val response = generateStandalone(target, finalRequest, localizedContext)
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
                val fallbackTarget = try { resolveFallbackExecutionTarget(request, workload, failedProviderId = target.providerId) }
                    catch (cancelled: CancellationException) {
                        recordExecutionFailure(target, cancelled, "CANCEL", startedAtMs, requestId)
                        throw cancelled
                    }
                if (fallbackTarget != null) {
                    val fallbackRequest = request.copy(modelId = fallbackTarget.modelId)
                    val fallbackRequestId = UUID.randomUUID().toString()
                    _executionDiagnostics.update { current ->
                        if (current.diagnosticsId == requestId) current.copy(providerId = fallbackTarget.providerId,
                            modelId = fallbackTarget.modelId, diagnosticsId = fallbackRequestId) else current
                    }
                    try {
                    eventBus?.publish(ProviderRequest(requestId = fallbackRequestId, providerId = fallbackTarget.providerId))
                    if (!fallbackTarget.provider.isLocal) eventBus?.publish(RemoteProviderRequestEvent(
                        interactionId = request.interactionId, requestId = fallbackRequestId,
                        providerId = fallbackTarget.providerId, modelId = fallbackTarget.modelId,
                        routingPolicy = policy.name, role = roleResolver.determineRole(workload).name,
                        reason = "Pre-output provider fallback"))
                        fallbackRequest.validateAgainst(fallbackTarget.providerCapabilities)
                        validateModelWorkload(fallbackTarget, fallbackRequest, workload)
                        val fallbackResponse = generateStandalone(fallbackTarget, fallbackRequest, localizedContext)
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
                        recordExecutionFailure(fallbackTarget, fallbackError, "GENERATION", startedAtMs, fallbackRequestId)
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
        val completed = AiExecutionDiagnostics(
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
        _executionDiagnostics.update { if (it.diagnosticsId == diagnosticsId) completed else it }
    }

    private fun recordExecutionFailure(
        target: AiExecutionTarget,
        error: Throwable,
        stage: String,
        startedAtMs: Long,
        diagnosticsId: String,
    ) {
        val completedAtMs = System.currentTimeMillis()
        val completed = AiExecutionDiagnostics(
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
        _executionDiagnostics.update { if (it.diagnosticsId == diagnosticsId) completed else it }
    }

    companion object {
        /** Live on-device provider. The only local provider ID new code may reference. */
        const val LITERT_PROVIDER_ID = "provider.litert.local"

        /** Retired llama.cpp provider ID. Kept for reading (and rewriting) persisted config only. */
        const val LEGACY_LLAMACPP_PROVIDER_ID = "provider.llamacpp.native"
    }
}
