package com.halo.app.audio

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.ai.LocalModelPhoneFixtures
import com.halo.app.ai.phoneRoleSuitability
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.LiteRtArtifactInspector
import com.halo.providers.litert.runtime.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Agent entry point: one installed artifact/backend/language, no downloads or role setters. */
@RunWith(AndroidJUnit4::class)
class LocalModelRolePhoneTest {
    private data class Observation(val role: AiModelRole, val pass: Boolean, val elapsedMs: Long)

    @Test fun qualifyInstalledRoles() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val graph = ManualDependencyGraph.providerGraph
        val provider = graph.providers.inference as LiteRtLocalProvider
        val modelId = requireNotNull(args.getString("modelId"))
        val language = requireNotNull(args.getString("language", "EN"))
        val fixtures = LocalModelPhoneFixtures.cases(language)
        val repetitions = requireNotNull(args.getString("repetitions", "3")).toInt().also { require(it in 1..5) }
        val backend = LocalComputeBackend.valueOf(requireNotNull(args.getString("backend")))
        require(backend in setOf(LocalComputeBackend.CPU, LocalComputeBackend.GPU))
        val descriptor = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        requireSelectedInstalledArtifact(descriptor, args)
        val requestedContext = args.getString("contextTokens")?.toInt() ?: 0
        val output = File(context.filesDir, "local-model-role-evidence.jsonl")
        fun record(stage: String, role: AiModelRole? = null, fill: JSONObject.() -> Unit = {}) {
            val row = JSONObject().put("fixtureSuite", LocalModelPhoneFixtures.VERSION)
                .put("runId", requireNotNull(args.getString("runId")))
                .put("sourceStamp", requireNotNull(args.getString("sourceStamp")))
                .put("modelId", modelId).put("sha256", descriptor.sha256).put("bytes", descriptor.bytes)
                .put("backend", backend.name).put("language", language).put("repetitions", repetitions)
                .put("requestedContext", requestedContext).put("runtime", LiteRtLocalProvider.RUNTIME_VERSION)
                .put("device", Build.MODEL).put("androidBuild", Build.FINGERPRINT)
                .put("role", role?.name ?: JSONObject.NULL).put("stage", stage).put("atMs", System.currentTimeMillis())
            row.fill()
            output.appendText(row.toString() + "\n")
        }
        record("STARTED")
        fun admissionAllowed(): Boolean {
            val state = ManualDependencyGraph.cognitiveSession.state.value
            return state is CognitiveSessionState.Idle || state is CognitiveSessionState.Completed || state is CognitiveSessionState.Failed
        }
        check(admissionAllowed()) { "RESOURCE_BUSY" }
        val metadata = LiteRtArtifactInspector(graph.nativeResourceCoordinator, graph.modelManager).inspect(descriptor)
        require(metadata.task == "LLM") { "Installed model task is non-generative" }
        val visionSupported = metadata.vision && descriptor.supportsVision
        val original = provider.loadConfigFor(descriptor)
        val profileBefore = ManualDependencyGraph.localInferenceProfileSource.entity(provider.id, modelId)
        val config = original.copy(computeBackend = backend, visionEnabled = visionSupported).let {
            if (requestedContext > 0) it.copy(contextSize = requestedContext, useModelDefaultContext = false) else it
        }
        record("CONFIGURATION") {
            put("cpuThreads", config.cpuThreads).put("gpuLayers", config.gpuLayers).put("visionEnabled", config.visionEnabled)
            put("modelDefaultContext", config.useModelDefaultContext).put("profileContext", config.contextSize)
            put("tuning", provider.preferences.tuning(modelId).toString())
            put("textOutputBudget", 256).put("reasoningOutputBudget", 512).put("caseDeadlineMs", 120_000)
        }
        val observations = mutableListOf<Observation>()
        var effectiveBackend: String? = null
        var effectiveContext: Int? = null
        val sampler = NativeFootprintSampler(context, modelId, descriptor.sha256, args.getString("sourceStamp"), args.getString("runId"))
        var saved = false
        var measured: LocalModelRoleQualification? = null
        try {
            val rows = provider.runRoleQualification(modelId, diagnosticConfig = config,
                admissionAllowed = ::admissionAllowed, diagnosticProbes = { _, generate ->
                    effectiveBackend = provider.modelSession.activeBackend?.name
                    effectiveContext = provider.modelSession.effectiveContextSize
                    suspend fun probe(id: String, role: AiModelRole, expected: String, request: AiRequest, history: List<AIMessage> = emptyList()) {
                        record("CASE_STARTED", role) { put("caseId", id) }
                        val started = SystemClock.elapsedRealtime()
                        var reason = "WRONG_ANSWER"
                        val response = try {
                            withTimeoutOrNull(120_000) { generate(request, AiContext(conversation = history)) }
                                .also { if (it == null) reason = "TIMEOUT" }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { reason = "GENERATION_FAILED"; null }
                        val matched = response != null && com.halo.app.ai.PhoneModelCase(id, role, "", expected).matches(response.text) && response.toolCalls.isEmpty()
                        val targetMatches = response?.providerId == provider.id && response.resolvedModelId == modelId && response.executionBackend == backend.name
                        val pass = matched && targetMatches
                        if (!targetMatches && response != null) reason = "TARGET_OR_BACKEND_CHANGED"
                        val elapsed = SystemClock.elapsedRealtime() - started
                        observations += Observation(role, pass, elapsed)
                        record("CASE_FINISHED", role) {
                            put("caseId", id).put("passed", pass).put("reason", if (pass) JSONObject.NULL else reason)
                            put("elapsedMs", elapsed).put("effectiveBackend", response?.executionBackend ?: JSONObject.NULL)
                            put("ttftMs", response?.metrics?.timeToFirstTokenMs ?: JSONObject.NULL)
                            put("outputTokens", response?.metrics?.outputTokens ?: JSONObject.NULL)
                        }
                    }
                    repeat(repetitions) { repeatIndex ->
                        for (case in fixtures) probe("${case.id}-$repeatIndex", case.role, case.expected,
                            AiRequest(text = case.prompt, modelId = modelId, maxOutputTokens = if (case.role == AiModelRole.REASONING) 512 else 256,
                                toolUseMode = ToolUseMode.NONE, reasoningMode = if (case.role == AiModelRole.REASONING) AiReasoningMode.ENABLED else AiReasoningMode.DISABLED), case.history)
                    }
                    // Paired image fixtures prevent a constant color answer from passing vision.
                    if (visionSupported) repeat(repetitions) { repeatIndex ->
                        for ((color, name) in listOf(Color.RED to "red", Color.BLUE to "blue", Color.GREEN to "green")) {
                            val bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                            val bytes = try {
                                bitmap.eraseColor(color)
                                ByteArrayOutputStream().use { stream ->
                                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)); stream.toByteArray()
                                }
                            } finally { bitmap.recycle() }
                            probe("vision-$name-$repeatIndex", AiModelRole.VISION, name,
                                AiRequest(text = "What color fills this image? Reply with exactly red, blue, or green in English.", image = bytes,
                                    mimeTypes = listOf("image/png"), modelId = modelId, maxOutputTokens = 256,
                                    toolUseMode = ToolUseMode.NONE, reasoningMode = AiReasoningMode.DISABLED))
                        }
                    }
                }, agentic = { _, generate ->
                    val protocol = provider.toolCallProtocol(modelId)
                    if (!protocol.supportsToolLoop) LocalAgenticQualification(LocalRoleProbeVerdict.NOT_APPLICABLE, protocol,
                        failure = LocalRoleProbeFailure.TOOL_PROTOCOL_UNAVAILABLE)
                    else {
                        var allInitial = true
                        var allContinuation = true
                        repeat(repetitions) { index ->
                            val started = SystemClock.elapsedRealtime()
                            record("CASE_STARTED", AiModelRole.AGENTIC) { put("caseId", "tool-loop-$index") }
                            var initial = false
                            var continuation = false
                            try {
                                withTimeout(120_000) {
                                    val tool = ToolDefinition("fixture_lookup", "fixture_lookup", "Look up a synthetic test code; no real action.",
                                        mapOf("type" to "object", "properties" to mapOf("key" to mapOf("type" to "string")), "required" to listOf("key")))
                                    val prompt = when (language) {
                                        "LV" -> "Izsauc fixture_lookup ar key=sample. Pēc rezultāta saņemšanas atbildi tikai ar iegūto kodu."
                                        "RU" -> "Вызови fixture_lookup с key=sample. После получения результата ответь только полученным кодом."
                                        else -> "Call fixture_lookup with key=sample. After receiving its result, reply with only the returned code."
                                    }
                                    val request = AiRequest(text = prompt, modelId = modelId, maxOutputTokens = 256,
                                        toolUseMode = ToolUseMode.REQUIRED, reasoningMode = AiReasoningMode.DISABLED)
                                    val toolContext = AiContext(availableTools = listOf(tool))
                                    val response = generate(request, toolContext)
                                    val call = response.toolCalls.singleOrNull()
                                    initial = response.providerId == provider.id && response.resolvedModelId == modelId && response.executionBackend == backend.name &&
                                        call != null && call.id.isNotBlank() && call.functionName == tool.name && call.arguments == mapOf("key" to "sample")
                                    if (initial && call != null) {
                                        val code = listOf("PLUM-73", "BIRCH-49", "CEDAR-18", "ELM-65", "ASH-92")[index]
                                        val next = generate(request.copy(appendTextAsUserMessage = false, turnKind = ProviderTurnKind.TOOL_CONTINUATION),
                                            toolContext.copy(conversation = listOf(AIMessage(Role.USER, prompt),
                                                AIMessage(Role.ASSISTANT, response.text, toolCalls = response.toolCalls),
                                                AIMessage(Role.TOOL, "{\"code\":\"$code\"}", toolCallId = call.id))))
                                        continuation = next.providerId == provider.id && next.resolvedModelId == modelId && next.executionBackend == backend.name &&
                                            next.toolCalls.isEmpty() && com.halo.app.ai.PhoneModelCase("tool", AiModelRole.AGENTIC, "", code).matches(next.text)
                                    }
                                }
                            } catch (_: TimeoutCancellationException) { /* failed bounded attempt */ }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (_: Exception) { /* safe bounded failure; no raw arguments or output exported */ }
                            allInitial = allInitial && initial
                            allContinuation = allContinuation && continuation
                            val elapsed = SystemClock.elapsedRealtime() - started
                            observations += Observation(AiModelRole.AGENTIC, initial && continuation, elapsed)
                            record("CASE_FINISHED", AiModelRole.AGENTIC) { put("caseId", "tool-loop-$index").put("passed", initial && continuation)
                                put("initialToolPass", initial).put("continuationPass", continuation).put("elapsedMs", elapsed) }
                        }
                        LocalAgenticQualification(if (allInitial && allContinuation) LocalRoleProbeVerdict.PASS else LocalRoleProbeVerdict.FAIL,
                            protocol, allInitial, allContinuation, failure = if (!allInitial) LocalRoleProbeFailure.TOOL_CALL_INVALID else if (!allContinuation) LocalRoleProbeFailure.TOOL_CONTINUATION_FAILED else null)
                    }
                })
            measured = rows.single()
            // Existing production evidence is English suite v2, not a multilingual recommendation store.
            // Save only compatible current-profile evidence; expanded failures conservatively veto its passes.
            val row = requireNotNull(measured)
            val fresh = graph.modelManager.verifyPhysicalArtifact(modelId)
            check(fresh != null && fresh.sha256 == descriptor.sha256 && fresh.bytes == descriptor.bytes) { "Artifact fingerprint changed" }
            if (language == "EN" && row.failure == null && row.backend == backend && provider.loadConfigFor(descriptor) == original &&
                !original.useModelDefaultContext && original.contextSize == row.contextTokens && original.cpuThreads == row.threads &&
                (original.computeBackend == LocalComputeBackend.AUTO || original.computeBackend == row.backend) && original.visionEnabled == config.visionEnabled) {
                // Preserve the baseline's actual scores. The legacy store has no extended-case
                // identity: a negative extended result can only conservatively veto overall
                // qualification. The export retains the precise per-role grades and case counts.
                val conservative = if (observations.any { !it.pass }) row.copy(failure = LocalRoleProbeFailure.WRONG_ANSWER) else row
                val retained = com.halo.app.ui.screens.settings.retainRoleQualification(
                    ManualDependencyGraph.modelRuntimeProfileRepository, profileBefore, conservative)
                saved = ManualDependencyGraph.localRoleQualificationRepository.save(retained)
                ManualDependencyGraph.workloadQualifications.recordRoleSuite(retained, provider, descriptor,
                    graph.nativeResourceCoordinator.budget.value.allowConcurrentHeavyweights)
            }
            for (role in AiModelRole.entries) {
                val samples = observations.filter { it.role == role }
                val expected = when (role) {
                    AiModelRole.AGENTIC -> repetitions
                    AiModelRole.VISION -> if (visionSupported) 3 * repetitions else 0
                    else -> fixtures.count { it.role == role } * repetitions
                }
                val median = samples.map { it.elapsedMs }.sorted().takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
                val stable = row.fast?.verdict == LocalRoleProbeVerdict.PASS && row.fast?.warmResidencyProven == true && row.memoryPressure == "NORMAL"
                val baselinePass = when (role) {
                    AiModelRole.FAST -> row.fast?.verdict == LocalRoleProbeVerdict.PASS && row.chat?.verdict == LocalRoleProbeVerdict.PASS
                    AiModelRole.CHAT -> row.chat?.verdict == LocalRoleProbeVerdict.PASS
                    AiModelRole.REASONING -> row.reasoning?.verdict == LocalRoleProbeVerdict.PASS && row.chat?.verdict == LocalRoleProbeVerdict.PASS
                    AiModelRole.AGENTIC -> row.agentic?.verdict == LocalRoleProbeVerdict.PASS
                    AiModelRole.VISION -> true
                }
                var grade = when {
                    row.failure != null -> "NOT_TESTED"
                    row.backend != backend -> "NOT_TESTED"
                    role == AiModelRole.VISION && !visionSupported -> "UNSUPPORTED"
                    role == AiModelRole.AGENTIC && row.agentic?.verdict == LocalRoleProbeVerdict.NOT_APPLICABLE -> "UNSUPPORTED"
                    !stable || !baselinePass -> "NOT_SUITABLE"
                    else -> phoneRoleSuitability(samples.size, expected, samples.count { it.pass }, median)
                }
                // Tool and vision suites are narrower: never label them GOOD from these few cases.
                if (role in setOf(AiModelRole.AGENTIC, AiModelRole.VISION) && samples.size == expected && expected > 0 && row.failure == null && row.backend == backend)
                    grade = if (stable && baselinePass && samples.all { it.pass }) "SUITABLE" else "NOT_SUITABLE"
                record("COMPLETED", role) {
                    put("grade", grade).put("scope", "CONTROLLED_FIXTURES_ONLY").put("passed", samples.count { it.pass }).put("attempted", samples.size).put("expected", expected)
                    put("medianAnswerMs", median ?: JSONObject.NULL).put("effectiveBackend", effectiveBackend ?: row.backend?.name ?: JSONObject.NULL)
                    put("effectiveContext", effectiveContext ?: row.contextTokens ?: JSONObject.NULL).put("suiteFailure", row.failure?.name ?: JSONObject.NULL)
                    put("baseRoleSuite", ROLE_PROBE_SUITE_VERSION).put("selectionEvidenceSaved", saved)
                    put("stable", stable).put("baselinePass", baselinePass).put("memoryPressure", row.memoryPressure ?: JSONObject.NULL)
                    put("sampledPeakPssKb", sampler.peakPssKb.get()).put("gpuMemory", "NOT_MEASURED")
                }
            }
        } finally { sampler.close() }
        // Semantic FAIL rows are valid completed observations, not instrumentation failures.
        assertTrue("Role suite could not complete: ${measured?.failure}", measured?.failure == null)
    }
}
