package com.halo.app.audio

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.ai.LocalIntentModelRouter
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.ModelInventoryRefreshReason
import com.halo.providers.litert.models.LiteRtArtifactTask
import com.halo.providers.litert.models.LiteRtModelCatalog
import com.halo.providers.litert.models.LiteRtModelTier
import com.halo.sdk.provider.CognitiveSessionState
import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.runtime.Storage
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs final-text intent advice only. No CognitiveSession input or tool dispatch. */
@RunWith(AndroidJUnit4::class)
class LocalIntentClassifierPhoneTest {
    private data class Case(val id: String, val language: String, val expected: String, val prompt: String, val preparedInput: String)
    private data class Matrix(val cases: List<Case>, val sha256: String)

    private fun matrix(): Matrix {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets.open("intent-classification-v1.json").use { it.readBytes() }
        val fixture = JSONObject(bytes.toString(Charsets.UTF_8))
        check(fixture.getInt("schema") == 1 && fixture.getString("scope") == "FINAL_TEXT_ADVISORY_ONLY")
        val cases = fixture.getJSONArray("cases").let { rows -> (0 until rows.length()).map { index ->
            rows.getJSONObject(index).let { row -> Case(row.getString("id"), row.getString("language"),
                row.getString("expected"), row.getString("prompt"), row.getString("preparedInput")) }
        } }
        check(cases.size == 48 && cases.map { it.id }.toSet().size == 48)
        check(cases.groupingBy { it.language }.eachCount() == mapOf("EN" to 12, "RU" to 12, "LV" to 12, "PL" to 12))
        check(cases.all { it.prompt.length in 3..384 && it.expected in setOf("ACTION", "ANSWER", "UNCERTAIN") &&
            it.preparedInput in setOf("Voice", "Keyboard") })
        return Matrix(cases, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
    }

    @Test fun classifyPreparedFinalTextMatrix() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val modelId = requireNotNull(args.getString("modelId"))
        val candidate = requireNotNull(LiteRtModelCatalog.byId(modelId))
        require(candidate.task == LiteRtArtifactTask.GENERATION && candidate.supportsText && candidate.tier == LiteRtModelTier.SMALL)
        val runId = requireNotNull(args.getString("runId"))
        val stamp = requireNotNull(args.getString("sourceStamp"))
        val matrix = matrix()
        val cases = matrix.cases
        val fixtureSha = matrix.sha256

        val graph = Graph.providerGraph
        val provider = graph.providers.inference as LiteRtLocalProvider
        graph.modelManager.refresh(ModelInventoryRefreshReason.USER_REQUEST)
        val descriptor = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        requireSelectedInstalledArtifact(descriptor, args)
        require(descriptor.sha256 == candidate.sha256)
        fun idle(): Boolean = when (Graph.cognitiveSession.state.value) {
            is CognitiveSessionState.Idle, is CognitiveSessionState.Completed, is CognitiveSessionState.Failed -> true
            else -> false
        }
        // A fresh instrumentation process must not disturb an existing chat engine.
        check(idle() && !provider.inferenceBusyForIntent() && provider.residentModelIdForIntent() == null) { "RESOURCE_BUSY_OR_MODEL_ALREADY_RESIDENT" }
        val preferences = MemoryStorage().apply {
            putString(LocalIntentModelRouter.MODEL_KEY, modelId)
            putBoolean(LocalIntentModelRouter.ENABLED_KEY, true)
        }
        val router = LocalIntentModelRouter(preferences, graph.modelManager) { provider }
        val output = File(instrumentation.targetContext.filesDir, "intent-model-acceptance.jsonl")
        fun record(stage: String, fill: JSONObject.() -> Unit = {}) {
            val row = JSONObject().put("schema", 1).put("scope", "FINAL_TEXT_ADVISORY_ONLY")
                .put("runId", runId).put("sourceStamp", stamp).put("fixtureSha256", fixtureSha)
                .put("modelId", modelId).put("sha256", descriptor.sha256).put("bytes", descriptor.bytes)
                .put("stage", stage).put("atMs", System.currentTimeMillis())
                .put("device", android.os.Build.MODEL).put("api", android.os.Build.VERSION.SDK_INT)
                .put("runtime", LiteRtLocalProvider.RUNTIME_VERSION)
                .put("effectiveBackend", provider.modelSession.activeBackend?.name ?: JSONObject.NULL)
                .put("toolExecution", "NOT_RUN").put("spokenRecognition", "NOT_RUN")
            row.fill()
            output.appendText(row.toString() + "\n")
        }
        var attempted = 0
        var matched = 0
        var noAdvice = 0
        var warmCases = 0
        record("STARTED")
        suspend fun observe(case: Case, phase: String): Boolean {
            check(idle()) { "INTERACTION_BECAME_ACTIVE" }
            val residentBefore = provider.residentModelIdForIntent()
            record("CASE_STARTED") {
                put("caseId", case.id).put("language", case.language).put("phase", phase)
                put("warmAtStart", residentBefore == modelId)
            }
            val started = SystemClock.elapsedRealtime()
            val decision = router.classify(case.prompt)?.name
            val elapsed = SystemClock.elapsedRealtime() - started
            record("CASE_FINISHED") {
                put("caseId", case.id).put("language", case.language).put("phase", phase)
                put("warmAtStart", residentBefore == modelId)
                put("expected", case.expected).put("observed", decision ?: JSONObject.NULL)
                put("elapsedMs", elapsed).put("matched", decision == case.expected)
                put("result", when { decision == null -> "NO_ADVICE"; decision == case.expected -> "MATCH"; else -> "WRONG_LABEL" })
            }
            if (phase == "PREPARED_MATRIX") {
                attempted++
                if (residentBefore == modelId) warmCases++
                if (decision == case.expected) matched++
                if (decision == null) noAdvice++
            }
            return decision == case.expected
        }
        try {
            observe(cases.first(), "FIRST_COLD_CASE_ONLY")
            // This case intentionally leaves the engine cold. Production advice
            // never loads it; the separate explicit warm fixture covers inference.
            withTimeout(180_000) {
                for (case in cases) observe(case, "PREPARED_MATRIX")
            }
            record("COMPLETED") {
                put("attempted", attempted).put("expectedCases", cases.size).put("matched", matched).put("noAdvice", noAdvice)
                put("warmAtStartCases", warmCases)
                put("status", when { attempted != cases.size || noAdvice > 0 -> "NOT_TESTED"; matched == cases.size -> "FIXTURES_MATCHED"; else -> "FIXTURE_MISSES" })
            }
        } catch (failure: Throwable) {
            record("INCOMPLETE") { put("attempted", attempted).put("matched", matched).put("status", "NOT_TESTED") }
            throw failure
        }
        // Semantic misses are retained observations, not hidden by a first-case assertion.
        assertEquals(cases.size, attempted)
        assertTrue(output.isFile)
    }

    /** Explicit lab setup on the existing owner, then the unmodified production router. */
    @Test fun classifyWarmFinalTextMatrix() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val modelId = requireNotNull(args.getString("modelId"))
        require(args.getString("backend") == "CPU")
        val expectedContext = args.getString("contextTokens")?.toIntOrNull()?.also { require(it > 0) }
        val candidate = requireNotNull(LiteRtModelCatalog.byId(modelId))
        require(candidate.task == LiteRtArtifactTask.GENERATION && candidate.supportsText && candidate.tier == LiteRtModelTier.SMALL)
        val runId = requireNotNull(args.getString("runId"))
        val stamp = requireNotNull(args.getString("sourceStamp"))
        val matrix = matrix()
        val graph = Graph.providerGraph
        val descriptor = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        requireSelectedInstalledArtifact(descriptor, args)
        require(descriptor.sha256 == candidate.sha256)
        val provider = graph.providers.inference as LiteRtLocalProvider
        check(provider.residentModelIdForIntent() == null && !provider.inferenceBusyForIntent()) { "EXISTING_PROVIDER_BUSY" }
        check(graph.nativeResourceCoordinator.snapshot.value.none { it.state != com.halo.sdk.runtime.NativeResourceState.RELEASED &&
            it.kind == com.halo.sdk.runtime.NativeResourceKind.HEAVY_LLM }) { "NATIVE_OWNER_ALREADY_RESIDENT" }
        val storage = MemoryStorage().apply {
            putString(LocalIntentModelRouter.MODEL_KEY, modelId)
            putBoolean(LocalIntentModelRouter.ENABLED_KEY, true)
        }
        // Reuse the graph's single native owner and its existing configuration. A second
        // provider would collide with resource identity and the inventory's resident observer.
        val configured = provider.loadConfigFor(descriptor)
        val router = LocalIntentModelRouter(storage, graph.modelManager) { provider }
        val output = File(instrumentation.targetContext.filesDir, "intent-warm-acceptance.jsonl")
        fun record(stage: String, fill: JSONObject.() -> Unit = {}) {
            val row = JSONObject().put("schema", 1).put("scope", "WARM_FINAL_TEXT_ADVISORY_ONLY")
                .put("runId", runId).put("sourceStamp", stamp).put("fixtureSha256", matrix.sha256)
                .put("modelId", modelId).put("sha256", descriptor.sha256).put("bytes", descriptor.bytes)
                .put("stage", stage).put("atMs", System.currentTimeMillis())
                .put("device", android.os.Build.MODEL).put("api", android.os.Build.VERSION.SDK_INT)
                .put("runtime", LiteRtLocalProvider.RUNTIME_VERSION).put("backend", "CPU")
                .put("requestedContext", configured.contextSize).put("modelDefaultContext", configured.useModelDefaultContext)
                .put("effectiveBackend", provider.modelSession.activeBackend?.name ?: JSONObject.NULL)
                .put("effectiveContext", provider.modelSession.effectiveContextSize ?: JSONObject.NULL)
                .put("effectiveArtifactSha256", provider.modelSession.loadedArtifactSha256 ?: JSONObject.NULL)
                .put("selectionEvidenceSaved", false).put("toolExecution", "NOT_RUN").put("spokenRecognition", "NOT_RUN")
            row.fill()
            output.appendText(row.toString() + "\n")
        }
        fun idle(): Boolean = when (Graph.cognitiveSession.state.value) {
            is CognitiveSessionState.Idle, is CognitiveSessionState.Completed, is CognitiveSessionState.Failed -> true
            else -> false
        }
        val sampler = NativeFootprintSampler(instrumentation.targetContext, modelId, descriptor.sha256, stamp, runId)
        val durations = mutableListOf<Long>()
        var attempted = 0
        var matched = 0
        var noAdvice = 0
        var warm = 0
        var withinBudget = 0
        try {
            record("STARTED")
            check(idle()) { "INTERACTION_ACTIVE" }
            // Advice must see the same configured backend/context as the explicitly loaded
            // engine. Never change saved preferences or force a GPU setup merely for this test.
            val unmet = when {
                provider.backendResolutionForDescriptor(descriptor).effective != LocalComputeBackend.CPU -> "CPU_CONFIGURATION"
                expectedContext != null && (configured.useModelDefaultContext || configured.contextSize != expectedContext) -> "CONTEXT_CONFIGURATION"
                else -> null
            }
            if (unmet != null) {
                record("PREREQUISITE_UNMET") { put("reasonCode", unmet) }
                error("INTENT_PREREQUISITE run=$runId sha=${descriptor.sha256} reason=$unmet")
            }
            val initialized = withTimeout(120_000) { provider.initializeForWorkloadLab(modelId, fresh = false) }
            check(initialized.second == "CPU" && (expectedContext == null || provider.modelSession.effectiveContextSize == expectedContext))
            check(provider.modelSession.loadedArtifactSha256 == descriptor.sha256) { "WARM_ARTIFACT_CHANGED" }
            check(provider.residentModelIdForIntent() == modelId && !provider.inferenceBusyForIntent())
            record("EXPLICIT_WARM_SETUP_FINISHED") { put("initMs", initialized.first) }
            withTimeout(180_000) {
                for (case in matrix.cases) {
                    check(idle()) { "INTERACTION_BECAME_ACTIVE" }
                    val resident = provider.residentModelIdForIntent() == modelId && !provider.inferenceBusyForIntent()
                    record("CASE_STARTED") { put("caseId", case.id).put("language", case.language).put("warmAtStart", resident) }
                    val previousGeneration = provider.conversationDiagnostics.value?.generationId
                    val started = SystemClock.elapsedRealtime()
                    val label = router.classify(case.prompt)?.name
                    val elapsed = SystemClock.elapsedRealtime() - started
                    val diagnostics = provider.conversationDiagnostics.value?.takeIf { it.generationId != previousGeneration }
                    durations += elapsed
                    attempted++
                    if (resident) warm++
                    if (label == case.expected) matched++
                    if (label == null) noAdvice++
                    if (elapsed <= LocalIntentModelRouter.TIMEOUT_MS) withinBudget++
                    record("CASE_FINISHED") {
                        put("caseId", case.id).put("language", case.language).put("preparedInput", case.preparedInput)
                        put("warmAtStart", resident).put("expected", case.expected).put("observed", label ?: JSONObject.NULL)
                        put("elapsedMs", elapsed).put("withinBudget", elapsed <= LocalIntentModelRouter.TIMEOUT_MS)
                        put("result", when { label == null -> "NO_ADVICE"; label == case.expected -> "MATCH"; else -> "WRONG_LABEL" })
                        put("newGenerationObserved", diagnostics != null)
                        put("thinkingEnabled", diagnostics?.thinkingEnabled ?: JSONObject.NULL)
                        put("toolSchemaCount", diagnostics?.toolSchemaCount ?: JSONObject.NULL)
                        put("promptEchoSuspected", diagnostics?.promptEchoSuspected ?: JSONObject.NULL)
                    }
                }
            }
            val complete = attempted == matrix.cases.size && warm == attempted
            val passed = complete && noAdvice == 0 && matched == attempted && withinBudget == attempted
            val grade = if (!complete || noAdvice > 0) "NOT_TESTED" else if (passed) "FIXTURES_MATCHED_WITHIN_BUDGET" else "FIXTURE_OR_LATENCY_MISSES"
            val sorted = durations.sorted()
            record("COMPLETED") {
                put("attempted", attempted).put("expectedCases", matrix.cases.size).put("matched", matched)
                put("warmAtStartCases", warm).put("noAdvice", noAdvice).put("withinBudgetCases", withinBudget)
                put("medianMs", sorted[sorted.size / 2]).put("p95Ms", sorted[(kotlin.math.ceil(sorted.size * .95).toInt() - 1)])
                put("status", grade)
            }
            assertTrue("INTENT_OUTCOME run=$runId sha=${descriptor.sha256} result=$grade", passed)
        } catch (failure: Throwable) {
            record("TERMINATED") { put("attempted", attempted).put("failureType", failure.javaClass.simpleName) }
            throw failure
        } finally {
            // This is the app's existing provider, not a fixture-owned native handle.
            // Normal residency/eviction owns its cleanup. An outside-predicate followed
            // by unload could race and destroy a newer user's generation or model load.
            sampler.close()
            record("RETURNED_TO_RESIDENCY_OWNER") {
                put("manualUnload", false).put("nativeOwnershipRetained", provider.modelSession.hasRuntimeOwnership)
                put("sampledPeakPssKb", sampler.peakPssKb.get())
                put("sampledPeakNativeHeapBytes", sampler.peakNativeHeapBytes.get()).put("sampleIntervalMs", 200)
            }
        }
    }

    private class MemoryStorage : Storage {
        private val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
        override fun getString(key: String, defaultValue: String?) = values[key] as? String ?: defaultValue
        override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
        override fun getInt(key: String, defaultValue: Int) = values[key] as? Int ?: defaultValue
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, defaultValue: Boolean) = values[key] as? Boolean ?: defaultValue
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
        override fun clear() { values.clear() }
    }
}
