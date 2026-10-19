package com.halo.engines.ai

import com.halo.sdk.provider.*
import com.halo.sdk.runtime.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class RoleModelFabricTest {
    private class MemoryStorage : Storage {
        val values = mutableMapOf<String, Any?>()
        var batches = 0
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
        override fun putBatch(strings: Map<String, String?>, booleans: Map<String, Boolean>) {
            batches++; values.putAll(strings); values.putAll(booleans)
        }
    }
    private class Bus : EventBus {
        val recorded = mutableListOf<HaloEvent>()
        override suspend fun publish(event: HaloEvent) { recorded += event }
        override fun events(): Flow<HaloEvent> = emptyFlow()
        override fun <T : HaloEvent> observe(type: Class<T>): Flow<T> = emptyFlow()
    }
    private class Provider(
        override val id: String = "local", override val isLocal: Boolean = true,
        val exact: Set<AiCapability> = setOf(AiCapability.TEXT, AiCapability.TOOLS),
        val protocol: ToolCallProtocol = ToolCallProtocol.HALO_PROMPT,
        val staticTools: Boolean = false,
        var location: AiExecutionLocation = if (isLocal) AiExecutionLocation.ON_DEVICE else AiExecutionLocation.REMOTE,
        var evidence: Map<AiCapability, CapabilityEvidence> = emptyMap(),
    ) : AiInferenceProvider, ModelInputCapabilityReporting, ToolProtocolReporting, ModelReadinessReporting {
        override val displayName = id
        override val status = MutableStateFlow(ProviderStatus.READY)
        var lists = 0; var statusReads = 0; var generations = 0; var fail = false
        val requests = mutableListOf<AiRequest>()
        var ids = listOf("A", "B", "C")
        fun models() = ids.map { AiModel(it, it, id, setOf(AiCapability.TEXT, AiCapability.IMAGE),
            contextLength = 4096, supportsStreaming = false, supportsTools = staticTools, executionLocation = location, capabilityEvidence = evidence) }
        override suspend fun getCapabilities() = setOf(AiCapability.TEXT) // Aggregate is deliberately weaker.
        override suspend fun capabilitiesForModel(modelId: String) = if (modelId in ids) exact else emptySet()
        override suspend fun toolCallProtocol(modelId: String) = if (modelId in ids) protocol else ToolCallProtocol.NONE
        override suspend fun getStatus(): ProviderStatus { statusReads++; return status.value }
        override suspend fun statusForModel(modelId: String): ProviderStatus {
            statusReads++
            return if (status.value.state == ProviderStatus.State.MODEL_MISSING && modelId in ids) ProviderStatus.READY else status.value
        }
        override suspend fun listModels(): List<AiModel> { lists++; return models() }
        override suspend fun generate(request: AiRequest, context: AiContext): AiResponse {
            requests += request; generations++
            if (fail) throw AiProviderException(AiErrorType.GENERATION_FAILED, "failed")
            return AiResponse("done", providerId = id, modelId = request.modelId)
        }
        override fun stream(request: AiRequest, context: AiContext): Flow<AiStreamEvent> = emptyFlow()
        fun entries() = models().map { AiModelCatalogEntry(it, AiModelRuntimeMetadata(AiModelRef(id, it.id), location,
            installed = true, resident = false, requiresNetwork = !isLocal, requiresCredential = !isLocal), source = ModelCatalogueSource.LOCAL_INSTALLED) }
    }
    private fun router(storage: MemoryStorage, vararg providers: Provider, bus: EventBus? = null): DefaultAiProviderRouter {
        AiSelectionRepository(storage).writeExplicitSelection(PersistedAiSelection(providers.first().id, "A", AiRoutingPolicy.EXPLICIT))
        return DefaultAiProviderRouter(providers.toList(), storage, eventBus = bus)
    }

    @Test fun exactPromptCapabilityOverridesStaticFalseAndAggregate() = runTest {
        val p = Provider(); val r = router(MemoryStorage(), p)
        r.setRoleBinding(AiRoleBinding(AiModelRole.AGENTIC, p.id, "B", AiRoleSelection.EXPLICIT))
        val target = r.resolveExecutionTarget(AiRequest(text = "act", modelId = "A"), AiRoleRequirements.workload(AiModelRole.AGENTIC))
        assertEquals("B", target.modelId); assertTrue(AiCapability.TOOLS in target.effectiveCapabilities)
        val candidate = r.candidatesForRole(AiModelRole.AGENTIC, p.entries(), setOf(p.id)).first()
        assertTrue(candidate.eligible); assertEquals(ToolCallProtocol.HALO_PROMPT, candidate.toolProtocol)
    }
    @Test fun refreshedArtifactMetadataRemovesMissingRoleCandidateWithoutProviderCalls() = runTest {
        val provider = Provider()
        val router = router(MemoryStorage(), provider)
        val missing = provider.entries().map { it.copy(runtime = it.runtime.copy(installed = false)) }
        assertTrue(router.candidatesForRole(AiModelRole.FAST, missing, setOf(provider.id)).all {
            !it.eligible && AiRoleCandidateRejection.MODEL_UNAVAILABLE in it.rejectionReasons
        })
        assertTrue(router.candidatesForRole(AiModelRole.FAST, provider.entries(), setOf(provider.id)).all { it.eligible })
        assertEquals(0, provider.statusReads)
        assertEquals(0, provider.lists)
        assertEquals(0, provider.generations)
    }

    @Test fun exactTextOnlyOverridesStaticTrueAndNoProtocolRejectsTools() = runTest {
        for (p in listOf(Provider(exact = setOf(AiCapability.TEXT), staticTools = true), Provider(protocol = ToolCallProtocol.NONE))) {
            val r = router(MemoryStorage(), p)
            assertTrue(r.candidatesForRole(AiModelRole.AGENTIC, p.entries(), setOf(p.id)).all {
                !it.eligible && AiRoleCandidateRejection.TOOLS_UNAVAILABLE in it.rejectionReasons })
            r.setRoleBinding(AiRoleBinding(AiModelRole.AGENTIC, p.id, "B", AiRoleSelection.EXPLICIT))
            try { r.resolveExecutionTarget(AiRequest(text = "act"), AiRoleRequirements.workload(AiModelRole.AGENTIC)); fail() }
            catch (e: AiProviderException) { assertEquals(AiErrorType.UNSUPPORTED_CAPABILITY, e.type) }
        }
    }

    @Test fun browsingUsesOnlyCachedMetadataAndDoesNotMutateSelection() = runTest {
        val p = Provider(); val r = router(MemoryStorage(), p)
        AiModelRole.entries.forEach { r.candidatesForRole(it, p.entries(), setOf(p.id)) }
        assertEquals(0, p.lists); assertEquals(0, p.statusReads); assertEquals(0, p.generations)
        assertEquals("A", r.selectedModelId.value)
        val uninstalled = p.entries().map { it.copy(runtime = it.runtime.copy(installed = false)) }
        assertTrue(r.candidatesForRole(AiModelRole.FAST, uninstalled, setOf(p.id)).none { it.eligible })
        assertTrue(r.candidatesForRole(AiModelRole.FAST, p.entries(), emptySet()).none { it.eligible })
    }

    @Test fun nativeRanksBeforePromptButPrivacyAlwaysWins() = runTest {
        val local = Provider("local"); val remote = Provider("remote", false, protocol = ToolCallProtocol.NATIVE)
        val r = router(MemoryStorage(), local, remote)
        r.setRoleBinding(AiRoleBinding(AiModelRole.AGENTIC, null, null, AiRoleSelection.AUTOMATIC))
        val work = AiRoleRequirements.workload(AiModelRole.AGENTIC)
        assertEquals(remote.id, r.resolveExecutionTarget(AiRequest(text = "act"), work).providerId)
        assertEquals(local.id, r.resolveExecutionTarget(AiRequest(text = "act"), work.copy(allowRemote = false)).providerId)
        r.setRoutingPolicy(AiRoutingPolicy.LOCAL_ONLY)
        assertEquals(local.id, r.resolveExecutionTarget(AiRequest(text = "act"), work).providerId)
        val candidate = r.candidatesForRole(AiModelRole.AGENTIC, remote.entries(), setOf(remote.id)).first()
        assertEquals(ToolCallProtocol.NATIVE, candidate.toolProtocol)
        assertTrue(AiRoleCandidateRejection.POLICY_EXCLUDED in candidate.rejectionReasons)
    }

    @Test fun fastAutomaticPrefersReadyDefaultThenDeviceThenLan() = runTest {
        val local = Provider(); val remote = Provider("remote", false); val lan = Provider("lan", true, location = AiExecutionLocation.LOCAL_NETWORK)
        val r = router(MemoryStorage(), local, lan, remote)
        r.selectTarget(AiModelRef(remote.id, "B"))
        r.setRoleBinding(AiRoleBinding(AiModelRole.FAST, null, null, AiRoleSelection.AUTOMATIC))
        val work = AiRoleRequirements.workload(AiModelRole.FAST)
        assertEquals(AiModelRef(remote.id, "B"), r.resolveExecutionTarget(AiRequest(text = "hi"), work).let { AiModelRef(it.providerId, it.modelId) })
        remote.status.value = ProviderStatus(ProviderStatus.State.STANDBY, readiness = ProviderReadiness.ConfiguredCold)
        assertEquals(local.id, r.resolveExecutionTarget(AiRequest(text = "hi"), work).providerId)
        local.ids = emptyList()
        assertEquals(lan.id, r.resolveExecutionTarget(AiRequest(text = "hi"), work).providerId)
    }

    @Test fun explicitBindingsPersistAndDeletedOrMalformedNeverFallback() = runTest {
        val storage = MemoryStorage(); val p = Provider(); val remote = Provider("remote", false)
        val r = router(storage, p, remote)
        for (role in listOf(AiModelRole.FAST, AiModelRole.REASONING, AiModelRole.AGENTIC))
            r.setRoleBinding(AiRoleBinding(role, p.id, "B", AiRoleSelection.EXPLICIT))
        val batches = storage.batches
        val recreated = DefaultAiProviderRouter(listOf(p, remote), storage)
        assertEquals(batches, storage.batches)
        for (role in listOf(AiModelRole.FAST, AiModelRole.REASONING, AiModelRole.AGENTIC))
            assertEquals("B", recreated.resolveExecutionTarget(AiRequest(text = "hi"), AiRoleRequirements.workload(role)).modelId)
        p.ids = listOf("A", "C")
        try { recreated.execute(AiRequest(text = "hi"), AiRoleRequirements.workload(AiModelRole.AGENTIC)); fail() }
        catch (e: AiProviderException) { assertEquals(AiErrorType.MODEL_NOT_FOUND, e.type) }
        assertEquals("B", recreated.roleBinding(AiModelRole.AGENTIC).modelId)
        assertEquals(0, remote.generations)
        recreated.setRoleBinding(AiRoleBinding(AiModelRole.FAST, null, "B", AiRoleSelection.EXPLICIT))
        try { recreated.resolveExecutionTarget(AiRequest(text = "hi"), AiRoleRequirements.workload(AiModelRole.FAST)); fail() }
        catch (e: AiProviderException) { assertEquals(AiErrorType.MODEL_UNAVAILABLE, e.type) }
        recreated.setRoleBinding(AiRoleBinding(AiModelRole.FAST, p.id, null, AiRoleSelection.EXPLICIT))
        try { recreated.resolveExecutionTarget(AiRequest(text = "hi"), AiRoleRequirements.workload(AiModelRole.FAST)); fail() }
        catch (e: AiProviderException) { assertEquals(AiErrorType.MODEL_UNAVAILABLE, e.type) }
        recreated.setRoleBinding(AiRoleBinding(AiModelRole.FAST, null, null, AiRoleSelection.DISABLED))
        try { recreated.resolveExecutionTarget(AiRequest(text = "hi"), AiRoleRequirements.workload(AiModelRole.FAST)); fail() }
        catch (e: AiProviderException) { assertEquals(AiErrorType.CAPABILITY_DISABLED, e.type) }
    }

    @Test fun inheritUsesConversationPreferenceEvenUnderAutomaticGlobalPolicy() = runTest {
        val default = Provider("z-default"); val other = Provider("a-other")
        val r = router(MemoryStorage(), default, other)
        r.setRoutingPolicy(AiRoutingPolicy.AUTOMATIC)
        default.status.value = ProviderStatus(ProviderStatus.State.STANDBY, readiness = ProviderReadiness.ConfiguredCold)
        r.setRoleBinding(AiRoleBinding(AiModelRole.FAST, null, null, AiRoleSelection.INHERIT_DEFAULT))
        val target = r.resolveExecutionTarget(AiRequest(text = "hi"), AiRoleRequirements.workload(AiModelRole.FAST))
        assertEquals(default.id, target.providerId)
        assertEquals("A", target.modelId)
    }

    @Test fun missingConversationDefaultCannotBlockInstalledRoleOrPinnedModel() = runTest {
        val p = Provider(); val r = router(MemoryStorage(), p)
        p.ids = listOf("B", "C")
        p.status.value = ProviderStatus(ProviderStatus.State.MODEL_MISSING)
        r.setRoleBinding(AiRoleBinding(AiModelRole.FAST, p.id, "B", AiRoleSelection.EXPLICIT))
        val work = AiRoleRequirements.workload(AiModelRole.FAST)
        assertEquals("B", r.resolveExecutionTarget(AiRequest(text = "hi"), work).modelId)
        assertEquals("B", r.resolvePinnedExecutionTarget(AiRequest(text = "hi"), work, p.id, "B").modelId)
        assertEquals("A", r.selectedModelId.value)
        try { r.resolveExecutionTarget(AiRequest(text = "hi")); fail() }
        catch (e: AiProviderException) { assertEquals(AiErrorType.MODEL_NOT_FOUND, e.type) }
    }

    @Test fun exactLocalIdsAlternateAndPinnedTurnSurvivesBindingChange() = runTest {
        val p = Provider(); val r = router(MemoryStorage(), p)
        r.setRoleBinding(AiRoleBinding(AiModelRole.FAST, p.id, "B", AiRoleSelection.EXPLICIT))
        r.setRoleBinding(AiRoleBinding(AiModelRole.AGENTIC, p.id, "C", AiRoleSelection.EXPLICIT))
        for (role in listOf(AiModelRole.CHAT, AiModelRole.FAST, AiModelRole.CHAT))
            r.execute(AiRequest(text = "hi"), AiRoleRequirements.workload(role))
        assertEquals(listOf("A", "B", "A"), p.requests.map { it.modelId })
        val work = AiRoleRequirements.workload(AiModelRole.AGENTIC)
        val target = r.resolveExecutionTarget(AiRequest(text = "act"), work)
        r.setRoleBinding(AiRoleBinding(AiModelRole.AGENTIC, p.id, "B", AiRoleSelection.EXPLICIT))
        val pinned = r.resolvePinnedExecutionTarget(AiRequest(text = "continue"), work, target.providerId, target.modelId)
        assertEquals("C", pinned.modelId); assertTrue(AiCapability.TOOLS in pinned.effectiveCapabilities)
        assertEquals("B", r.resolveExecutionTarget(AiRequest(text = "new"), work).modelId)
        r.setRoleBinding(AiRoleBinding(AiModelRole.FAST, null, null, AiRoleSelection.INHERIT_DEFAULT))
        assertEquals("A", r.resolveExecutionTarget(AiRequest(text = "hi"), AiRoleRequirements.workload(AiModelRole.FAST)).modelId)
    }

    @Test fun visionRequiresVerifiedImageDespiteExactImageReporting() = runTest {
        val p = Provider(exact = setOf(AiCapability.TEXT, AiCapability.IMAGE)); val r = router(MemoryStorage(), p)
        assertTrue(r.candidatesForRole(AiModelRole.VISION, p.entries(), setOf(p.id)).none { it.eligible })
        p.evidence = mapOf(AiCapability.IMAGE to CapabilityEvidence.HALO_PROBE_SUCCESS)
        assertTrue(r.candidatesForRole(AiModelRole.VISION, p.entries(), setOf(p.id)).all { it.eligible })
    }

    @Test fun fallbackPreservesInteractionAndActualRoleWithNewRequestIdentity() = runTest {
        val first = Provider("first", false, protocol = ToolCallProtocol.NATIVE).also { it.fail = true }
        val next = Provider("next", false, protocol = ToolCallProtocol.HALO_PROMPT)
        val bus = Bus(); val r = router(MemoryStorage(), first, next, bus = bus)
        r.setRoleBinding(AiRoleBinding(AiModelRole.AGENTIC, null, null, AiRoleSelection.AUTOMATIC))
        r.execute(AiRequest(text = "act", interactionId = "interaction-062"), AiRoleRequirements.workload(AiModelRole.AGENTIC))
        val traces = bus.recorded.filterIsInstance<RemoteProviderRequestEvent>()
        assertEquals(2, traces.size)
        assertTrue(traces.all { it.interactionId == "interaction-062" && it.role == "AGENTIC" })
        assertNotEquals(traces[0].requestId, traces[1].requestId)
        assertEquals("interaction-062", next.requests.single().interactionId)
    }
}
