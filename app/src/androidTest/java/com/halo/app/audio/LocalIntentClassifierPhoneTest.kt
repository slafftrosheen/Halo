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
    private data class Case(val id: String, val language: String, val expected: String, val prompt: String)

    @Test fun classifyPreparedFinalTextMatrix() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val modelId = requireNotNull(args.getString("modelId"))
        val candidate = requireNotNull(LiteRtModelCatalog.byId(modelId))
        require(candidate.task == LiteRtArtifactTask.GENERATION && candidate.supportsText && candidate.tier == LiteRtModelTier.SMALL)
        val runId = requireNotNull(args.getString("runId"))
        val stamp = requireNotNull(args.getString("sourceStamp"))
        val fixtureBytes = instrumentation.context.assets.open("intent-classification-v1.json").use { it.readBytes() }
        val fixture = JSONObject(fixtureBytes.toString(Charsets.UTF_8))
        check(fixture.getInt("schema") == 1 && fixture.getString("scope") == "FINAL_TEXT_ADVISORY_ONLY")
        val fixtureSha = MessageDigest.getInstance("SHA-256").digest(fixtureBytes).joinToString("") { "%02x".format(it) }
        val cases = fixture.getJSONArray("cases").let { rows -> (0 until rows.length()).map { index ->
            rows.getJSONObject(index).let { row -> Case(row.getString("id"), row.getString("language"),
                row.getString("expected"), row.getString("prompt")) }
        } }
        check(cases.size == 48 && cases.map { it.id }.toSet().size == 48)
        check(cases.groupingBy { it.language }.eachCount() == mapOf("EN" to 12, "RU" to 12, "LV" to 12, "PL" to 12))
        check(cases.all { it.prompt.length in 3..384 && it.expected in setOf("ACTION", "ANSWER", "UNCERTAIN") })

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
            // Only production advisory admission may load/reuse the candidate.
            // No explicit warm load or unload: outer prechecks cannot reserve
            // identity across a racing chat/model switch. Record actual warmth.
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
