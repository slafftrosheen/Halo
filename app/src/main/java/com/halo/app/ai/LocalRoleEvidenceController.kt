package com.halo.app.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.data.models.*
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.*
import com.halo.providers.litert.runtime.ROLE_PROBE_SUITE_VERSION
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LocalRoleEvidenceState(val history: List<LocalRoleQualificationEntity> = emptyList(),
    val current: List<LocalRoleQualificationEntity> = emptyList(),
    val recommendations: Map<AiModelRole, LocalRoleRecommendation> = emptyMap())

/** Shared read-only projection; the sole mutation path is the explicit apply action. */
class LocalRoleEvidenceController(private val repository: LocalRoleQualificationRepository,
    private val inventory: LocalModelInventoryRepository, private val provider: () -> LiteRtLocalProvider?,
    private val control: ProviderControlRepository) : ViewModel() {
    // Serialize artifact verification and role writes across rapid user taps.
    private val applyMutex = Mutex()
    private val revision = MutableStateFlow(0L)
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    val state = combine(repository.history, inventory.inventoryState, control.roleCandidates, revision) { rows, _, eligible, _ ->
        project(rows, eligible)
    }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.Eagerly, LocalRoleEvidenceState())
    fun invalidate() { revision.update { it + 1 } }
    private fun identities(): Set<LocalRoleEvidenceIdentity> {
        val local = provider() ?: return emptySet()
        val ready = inventory.inventoryState.value as? LocalModelInventoryState.Ready ?: return emptySet()
        return ready.models.filter { it.valid && it.sha256 != null }.map { descriptor ->
            val config = local.loadConfigFor(descriptor)
            val backend = local.backendResolutionForDescriptor(descriptor).effective
            LocalRoleEvidenceIdentity(AiModelRef(local.id, descriptor.id), descriptor.sha256!!,
                repository.device.fingerprint, "LITERT", LiteRtLocalProvider.RUNTIME_VERSION,
                LOCAL_ROLE_QUALIFICATION_COMPAT_EPOCH, backend.name,
                config.contextSize.takeUnless { config.useModelDefaultContext }, config.cpuThreads,
                if (backend == LocalComputeBackend.CPU) 0 else config.gpuLayers, ROLE_PROBE_SUITE_VERSION)
        }.toSet()
    }
    private fun project(rows: List<LocalRoleQualificationEntity>, eligible: Map<AiModelRole, List<AiRoleCandidate>>): LocalRoleEvidenceState {
        val exact = identities()
        val current = rows.filter { it.identity() in exact }.distinctBy { it.identity() }
        return LocalRoleEvidenceState(rows, current, AiModelRole.entries.associateWith { role ->
            LocalRoleRecommendationEngine.recommend(role, rows.map { it.evidence() }, exact, eligible[role].orEmpty())
        })
    }
    fun applyRoleRecommendation(role: AiModelRole, ref: AiModelRef) {
        viewModelScope.launch(Dispatchers.IO) {
            applyMutex.withLock {
            _message.value = null
            // A model id alone is not globally unique across providers. Never verify a
            // different provider's model through the active LiteRT inventory.
            val activeProvider = provider()
            if (activeProvider == null || ref.providerId != activeProvider.id) {
                _message.value = "Selected model is not a verified local LiteRT model"
                return@withLock
            }
            val prior = state.value.recommendations[role]?.candidates?.firstOrNull { it.identity.ref == ref }
            val (expectedBinding, expectedDefault) = synchronized(control.router) {
                control.router.roleBinding(role) to Triple(control.router.selectedProviderId.value,
                    control.router.selectedModelId.value, control.router.routingPolicy.value)
            }
            val verified = inventory.verifyPhysicalArtifact(ref.modelId)
            val eligibility = control.router.candidatesForRole(role, control.catalogue.state.value.models,
                control.rows.value.filter { it.configured }.map { it.descriptor.providerId }.toSet())
            val refreshed = project(state.value.history, mapOf(role to eligibility)).recommendations[role]
            val ready = roleRecommendationProviderAvailable(activeProvider.statusForModel(ref.modelId).readiness)
            val applied = synchronized(control.router) {
                // The router's selection writes use this same monitor. A manual
                // assignment made during artifact hashing wins over this old tap.
                val unchanged = if (role == AiModelRole.CHAT)
                    expectedDefault == Triple(control.router.selectedProviderId.value,
                        control.router.selectedModelId.value, control.router.routingPolicy.value)
                else expectedBinding == control.router.roleBinding(role)
                unchanged && provider() === activeProvider &&
                    applyMeasuredRecommendation(role, ref, prior, refreshed, verified?.sha256,
                        verified?.valid == true && ready, control.router::selectTarget, control.router::setRoleBinding)
            }
            if (!applied) {
                _message.value = "Recommendation is stale — run the role suite again"
                invalidate()
                return@withLock
            }
            _message.value = "Configured ${role.name.lowercase()}"
            }
        }
    }
    fun report(source: String): String = buildString {
        appendLine("Halo source=$source roleProbeSuite=$ROLE_PROBE_SUITE_VERSION compatibilityEpoch=$LOCAL_ROLE_QUALIFICATION_COMPAT_EPOCH")
        for (r in state.value.current) {
            appendLine("model=${r.artifactId} sha=${r.artifactFingerprint.take(12)} backend=${r.backend} context=${r.contextTokens} threads=${r.threads} gpuLayers=${r.gpuLayers} testedAt=${r.testedAtMs}")
            appendLine("loadMs=${r.loadMs} coldMs=${r.coldTtftMs} warmMs=${r.warmTtftSamples} medianMs=${r.warmMedianMs} FAST=${r.fastVerdict} ${r.warmSamples}/3 CHAT=${r.chatPassed}/${r.chatTotal} REASONING=${r.reasoningPassed}/${r.reasoningTotal}")
            appendLine("AGENTIC=${r.agenticVerdict} protocol=${r.toolProtocol} initial=${r.initialToolPass} continuation=${r.continuationPass} VISION=${r.visionVerdict} memoryPressure=${r.memoryPressure} stable=${r.stable} failure=${r.failureCode}")
        }
    }
}

/** Physical verification and live eligibility are supplied by the explicit app boundary above. */
internal fun applyMeasuredRecommendation(role: AiModelRole, ref: AiModelRef,
    prior: LocalRoleCandidateEvidence?, current: LocalRoleRecommendation?, verifiedSha: String?,
    available: Boolean, selectTarget: (AiModelRef) -> Unit, setRoleBinding: (AiRoleBinding) -> Unit): Boolean {
    val latest = current?.candidates?.firstOrNull { it.identity.ref == ref }
    if (!available || prior == null || verifiedSha != prior.identity.artifactFingerprint ||
        latest?.identity != prior.identity || current.recommended != ref || !latest.stable || latest.memoryPressure == "CRITICAL") return false
    if (role == AiModelRole.CHAT) selectTarget(ref)
    else setRoleBinding(AiRoleBinding(role, ref.providerId, ref.modelId, AiRoleSelection.EXPLICIT))
    return true
}

internal fun roleRecommendationProviderAvailable(readiness: ProviderReadiness?): Boolean =
    readiness == ProviderReadiness.Ready || readiness == ProviderReadiness.ConfiguredCold
