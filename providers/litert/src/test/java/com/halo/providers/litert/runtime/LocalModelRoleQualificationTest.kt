package com.halo.providers.litert.runtime

import com.halo.providers.litert.models.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalModelRoleQualificationTest {
    private fun sample(ms: Long, cold: Boolean? = false, text: String = "ok") = RuntimeGenerationResult(text, tokenCount = 1, timeToFirstTokenMs = ms, coldStart = cold)
    @Test fun coldExcludedFromWarmMedian() {
        val result = fastQualification(listOf(sample(12000, true), sample(2800), sample(3000), sample(2900)), "CPU")
        assertEquals(2900L, result.medianWarmTtftMs); assertEquals("GOOD", result.latency)
        assertEquals(LocalRoleProbeVerdict.PASS, result.verdict)
    }
    @Test fun repeatedColdDoesNotProveWarmResidency() {
        val result = fastQualification(List(4) { sample(100, true) }, "CPU")
        assertEquals(LocalRoleProbeVerdict.FAIL, result.verdict)
        assertEquals(LocalRoleProbeFailure.WARM_RESIDENCY_NOT_PROVEN, result.failure)
    }
    @Test fun missingGenerationFailsWithTwoSamples() {
        val result = fastQualification(listOf(sample(12000, true), sample(2800), null, sample(2900)), "CPU")
        assertEquals(2, result.successfulWarmSamples); assertNull(result.medianWarmTtftMs)
        assertEquals(LocalRoleProbeVerdict.FAIL, result.verdict)
    }
    @Test fun narrowNormalizationAllowsQuotesCaseAndOnePeriodButNoProse() {
        for (answer in listOf("HALO_CHAT_OK", " \"HALO_CHAT_OK\" ", " halo_chat_ok ", "HALO_CHAT_OK.")) assertTrue(probeMatches(answer, "HALO_CHAT_OK"))
        assertFalse(probeMatches("Sure! HALO_CHAT_OK", "HALO_CHAT_OK"))
        assertTrue(probeMatches("136.", "136")); assertFalse(probeMatches("137", "136"))
        assertFalse(probeMatches("136..", "136"))
    }

    private class Inventory(ids: List<String>, thinking: Boolean = false) : LocalModelInventoryRepository {
        val models = ids.map { LocalModelDescriptor(it, it, it, 100, 0, "sha-$it", 1024, false, false, LocalModelValidation.VERIFIED_ARTIFACT, supportsThinking = thinking) }
        override val inventoryState = MutableStateFlow<LocalModelInventoryState>(LocalModelInventoryState.Ready(models, 0))
        val verified = mutableListOf<String>()
        var changed: String? = null
        override suspend fun verifyPhysicalArtifact(modelId: String): LocalModelDescriptor? {
            verified += modelId
            val model = resolveModel(modelId) ?: return null
            return if (changed == modelId && verified.count { it == modelId } > 1) model.copy(sha256 = "changed") else model
        }
        override suspend fun resolveModel(modelId: String) = models.find { it.id == modelId }
        override suspend fun refresh(reason: ModelInventoryRefreshReason) = Unit
        override fun invalidate(reason: String) = Unit
    }
    private class Runtime(val events: MutableList<String>) : LocalInferenceRuntime, LocalRuntimeBackendReporting {
        override val format = LocalModelFormat.LITERT_LM
        override val state = MutableStateFlow(LocalModelSessionState.IDLE)
        override var loadedModelId: String? = null
        override var activeBackend: LocalComputeBackend? = LocalComputeBackend.CPU
        override val backendFallbackReason: String? = null
        val configs = mutableListOf<RuntimeGenerationConfig>()
        var count = 0
        var failModel: String? = null
        var drift = false
        var block = false
        var blockModel: String? = null
        val entered = CompletableDeferred<Unit>()
        val resume = CompletableDeferred<Unit>()
        override fun isLoaded() = loadedModelId != null
        override suspend fun loadModel(modelId: String, modelPath: String, config: RuntimeLoadConfig): Result<RuntimeLoadResult> {
            events += "load:$modelId"
            if (modelId == failModel) return Result.failure(IllegalStateException("private exception detail"))
            loadedModelId = modelId; count = 0
            return Result.success(RuntimeLoadResult(LocalComputeBackend.CPU, 0, config.contextSize))
        }
        override suspend fun generate(config: RuntimeGenerationConfig, onToken: suspend (String) -> Unit): Result<RuntimeGenerationResult> {
            configs += config
            require(config.temperature == if (config.thinkingEnabled == true) 0.6 else 0.0)
            val index = count++
            require(config.maxTokens <= if (index < 4) 32 else if (index < 7) 64 else 192)
            events += "generate:$loadedModelId"
            if (block || loadedModelId == blockModel) { entered.complete(Unit); resume.await() }
            if (drift && index == 1) activeBackend = LocalComputeBackend.GPU
            val text = when (index) { 4 -> "HALO_CHAT_OK"; 5 -> "136"; 6 -> {
                require(config.initialMessages.size == 2); "COBALT-47"
            }; 7 -> "142"; 8 -> "Chris"; 9 -> "90"; else -> "ok" }
            return Result.success(RuntimeGenerationResult(text, tokenCount = 1, timeToFirstTokenMs = if (index == 0) 12000 else 2900,
                reasoningText = if (index >= 7) "private reasoning" else null, coldStart = index == 0))
        }
        override suspend fun cancelGeneration() { events += "cancel"; resume.complete(Unit) }
        override suspend fun unloadModel(): Result<Unit> {
            loadedModelId?.let { events += "unload:$it" }; loadedModelId = null
            return Result.success(Unit)
        }
    }
    private val noTools: suspend (LocalModelDescriptor, String) -> LocalAgenticQualification = { _, _ ->
        LocalAgenticQualification(LocalRoleProbeVerdict.NOT_APPLICABLE, ToolCallProtocol.NONE)
    }
    @Test fun versionTwoSeparatesChatFromBoundedNonGreedyReasoning() = runTest {
        val runtime = Runtime(mutableListOf())
        val inventory = Inventory(listOf("A"), thinking = true)
        val runner = LocalModelRoleQualificationRunner(LocalModelSession({ runtime }, backgroundScope), inventory)
        val row = runner.runSuite("A", { RuntimeLoadConfig() }, noTools)
        assertEquals(2, ROLE_PROBE_SUITE_VERSION)
        assertEquals(3, row.chat!!.passed)
        assertEquals(3, row.reasoning!!.passed)
        assertEquals(10, runtime.configs.size)
        runtime.configs.take(7).forEach { assertEquals(false, it.thinkingEnabled); assertEquals(0.0, it.temperature, 0.0) }
        runtime.configs.takeLast(3).forEach {
            assertEquals(true, it.thinkingEnabled); assertEquals(64, it.thinkingTokenBudget)
            assertTrue(it.temperature > 0.0); assertTrue(it.topK > 1)
        }
    }
    @Test fun serialMatrixSeparatesModelsAndContinuesAfterLoadFailure() = runTest {
        val events = mutableListOf<String>(); val runtime = Runtime(events).apply { failModel = "B" }
        val inventory = Inventory(listOf("A", "B", "C")); val session = LocalModelSession({ runtime }, backgroundScope)
        val rows = LocalModelRoleQualificationRunner(session, inventory).runRoleMatrix(inventory.models, { RuntimeLoadConfig() }, noTools)
        assertEquals(listOf("A", "B", "C"), rows.map { it.modelId })
        assertEquals(LocalRoleProbeFailure.LOAD_FAILED, rows[1].failure)
        assertEquals(listOf("load:A", "unload:A", "load:B", "load:C", "unload:C"), events.filterNot { it.startsWith("generate:") })
        for (row in listOf(rows[0], rows[2])) {
            assertEquals(3, row.chat!!.passed); assertEquals(3, row.reasoning!!.passed)
            assertEquals(51, row.reasoning.reasoningChars)
            assertEquals(LocalRoleProbeVerdict.PASS, row.fast!!.verdict)
        }
        assertNull(session.loadedModelId)
        assertEquals(listOf("A", "A", "B", "B", "C", "C"), inventory.verified)
    }
    @Test fun reportedResidencyCannotSilentlyLoseWarmTruth() {
        val result = fastQualification(listOf(sample(100, true), sample(100), sample(100, null), sample(100)), "CPU")
        assertEquals(LocalRoleProbeVerdict.FAIL, result.verdict)
        assertEquals(false, result.warmResidencyProven)
    }
    @Test fun unsupportedResidencyIsExplicitlyUnknown() {
        val result = fastQualification(List(4) { sample(100, null) }, "CPU")
        assertEquals(LocalRoleProbeVerdict.PASS, result.verdict)
        assertNull(result.warmResidencyProven)
    }
    @Test fun memoryPressureReportsWorstObservedDuringSuite() = runTest {
        val runtime = Runtime(mutableListOf()); val session = LocalModelSession({ runtime }, backgroundScope)
        val runner = LocalModelRoleQualificationRunner(session, Inventory(listOf("A"))) {
            if (runtime.count in 3..6) com.halo.sdk.runtime.NativeMemoryPressure.LOW else com.halo.sdk.runtime.NativeMemoryPressure.NORMAL
        }
        val row = runner.runSuite("A", { RuntimeLoadConfig() }, noTools)
        assertEquals("LOW", row.memoryPressure)
    }

    @Test fun changedArtifactDiscardsRoleEvidence() = runTest {
        val inventory = Inventory(listOf("A")).apply { changed = "A" }
        val session = LocalModelSession({ Runtime(mutableListOf()) }, backgroundScope)
        val row = LocalModelRoleQualificationRunner(session, inventory).runSuite("A", { RuntimeLoadConfig() }, noTools)
        assertEquals(LocalRoleProbeFailure.ARTIFACT_CHANGED, row.failure); assertNull(row.fast); assertNull(row.chat); assertNull(row.reasoning); assertNull(row.vision)
    }
    @Test fun backendDriftDiscardsMixedSamples() = runTest {
        val runtime = Runtime(mutableListOf()).apply { drift = true }; val session = LocalModelSession({ runtime }, backgroundScope)
        val row = LocalModelRoleQualificationRunner(session, Inventory(listOf("A"))).runSuite("A", { RuntimeLoadConfig() }, noTools)
        assertEquals(LocalRoleProbeFailure.BACKEND_CHANGED, row.failure); assertNull(row.fast)
    }
    @Test fun busyAdmissionDoesNotVerifyLoadOrUnload() = runTest {
        val events = mutableListOf<String>(); val inventory = Inventory(listOf("A")); val session = LocalModelSession({ Runtime(events) }, backgroundScope)
        session.tryBeginBenchmark("other")
        val row = LocalModelRoleQualificationRunner(session, inventory).runSuite("A", { RuntimeLoadConfig() }, noTools)
        assertEquals(LocalRoleProbeFailure.RESOURCE_BUSY, row.failure); assertTrue(events.isEmpty()); assertTrue(inventory.verified.isEmpty())
        session.endBenchmark("other")
    }
    @Test fun cancellationReleasesResidencyAndStopsRemainingModels() = runTest {
        val events = mutableListOf<String>(); val runtime = Runtime(events).apply { block = true }
        val inventory = Inventory(listOf("A", "C")); val session = LocalModelSession({ runtime }, backgroundScope)
        val job = launch { LocalModelRoleQualificationRunner(session, inventory).runRoleMatrix(inventory.models, { RuntimeLoadConfig() }, noTools) }
        runtime.entered.await(); job.cancelAndJoin()
        assertTrue("unload:A" in events); assertFalse("load:C" in events)
        assertEquals(InferenceAdmission.Granted, session.tryBeginBenchmark("next")); session.endBenchmark("next")
    }
    @Test fun validatedRowHookCompletesBeforeLaterModelCanBeCancelled() = runTest {
        val events = mutableListOf<String>(); val runtime = Runtime(events).apply { blockModel = "B" }
        val inventory = Inventory(listOf("A", "B", "C")); val session = LocalModelSession({ runtime }, backgroundScope)
        val retained = mutableListOf<String>()
        val job = launch {
            LocalModelRoleQualificationRunner(session, inventory).runRoleMatrix(inventory.models, { RuntimeLoadConfig() }, noTools,
                onRow = { row ->
                    if (row.failure == null) {
                        assertEquals(2, inventory.verified.count { it == row.modelId })
                        assertNull(session.loadedModelId)
                        retained += row.modelId
                    }
                })
        }
        runtime.entered.await()
        assertEquals(listOf("A"), retained)
        job.cancelAndJoin()
        assertEquals(listOf("A"), retained); assertFalse("load:C" in events)
    }

    @Test fun matrixUsesInstalledVerifiedOnlyWithoutAcquisition() = runTest {
        val events = mutableListOf<String>(); val inventory = Inventory(listOf("A", "C")); val session = LocalModelSession({ Runtime(events) }, backgroundScope)
        val unknown = inventory.models[0].copy(id = "unknown", validation = LocalModelValidation.UNVERIFIED)
        val missing = inventory.models[0].copy(id = "missing", validation = LocalModelValidation.INCOMPATIBLE)
        val rows = LocalModelRoleQualificationRunner(session, inventory).runRoleMatrix(inventory.models + unknown + missing, { RuntimeLoadConfig() }, noTools)
        assertEquals(listOf("A", "C"), rows.map { it.modelId })
    }
    @Test fun reportContainsOnlyBoundedEvidence() = runTest {
        val session = LocalModelSession({ Runtime(mutableListOf()) }, backgroundScope)
        val row = LocalModelRoleQualificationRunner(session, Inventory(listOf("A"))).runSuite("A", { RuntimeLoadConfig() }, noTools)
        val report = LocalModelRoleQualification.report(listOf(row), "source")
        assertTrue(report.contains("roleProbeSuite=2")); assertTrue(report.contains("medianMs=2900"))
        for (privateText in listOf("private reasoning", "HALO_CHAT_OK", "COBALT-47", "Reply with", "HALO_OK", "fingerprint")) assertFalse(report.contains(privateText))
    }
}
