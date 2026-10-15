package com.halo.app.audio

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.ModelInfo
import com.google.ai.edge.litertlm.Modality
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.models.LiteRtModelPreferences
import com.halo.providers.litert.models.ModelVerificationResult
import com.halo.providers.litert.runtime.LiteRtControlProbeRunner
import com.halo.sdk.provider.LocalComputeBackend
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** One artifact/backend per process: failures cannot hide another model's acceptance result. */
@RunWith(AndroidJUnit4::class)
class DownloadedModelDiagnosticsTest {
    /**
     * One warm Engine and multiple distinct native conversations. Unlike -e cycles,
     * this probes engine reuse, per-conversation release and recurrent-state behavior.
     * Explicit CPU/GPU only; never qualifies a backend or enables a user model role.
     */
    @Test fun diagnoseResidentEndurance() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val modelId = requireNotNull(args.getString("modelId"))
        val backend = LocalComputeBackend.valueOf(requireNotNull(args.getString("backend")))
        require(backend != LocalComputeBackend.AUTO)
        val turns = args.getString("residentTurns")?.toIntOrNull() ?: 5
        require(turns in 2..10)
        val graph = ManualDependencyGraph.providerGraph
        val provider = graph.providers.inference as LiteRtLocalProvider
        val descriptor = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        requireSelectedInstalledArtifact(descriptor, args)
        assertTrue("Unload selected model before native endurance", provider.unloadLocalModel())
        ModelInfo.from(descriptor.path).use { info ->
            require(info is ModelInfo.Llm) { "Installed model task is non-generative; metadata inspection only" }
        }
        val configuration = provider.loadConfigFor(descriptor).copy(computeBackend = backend).let { initial ->
            args.getString("contextTokens")?.toIntOrNull()
                ?.let { initial.copy(contextSize = it, useModelDefaultContext = false) } ?: initial
        }
        val sampler = NativeFootprintSampler(instrumentation.targetContext, modelId, descriptor.sha256, args.getString("sourceStamp"), args.getString("runId"))
        try {
        val output = File(instrumentation.targetContext.filesDir, "resident-model-endurance.jsonl")
        val manager = instrumentation.targetContext.getSystemService(android.app.ActivityManager::class.java)
        val row = JSONObject().put("modelId", modelId).put("sha256", descriptor.sha256)
            .put("runtime", LiteRtLocalProvider.RUNTIME_VERSION)
            .put("backend", backend.name).put("device", android.os.Build.MODEL)
            .put("sourceStamp", args.getString("sourceStamp")).put("runId", args.getString("runId"))
            .put("turns", turns)
            .put("requestedContext", if(configuration.useModelDefaultContext) "MODEL_DEFAULT" else configuration.contextSize)
        fun record(stage: String, turn: Int) {
            val info = android.app.ActivityManager.MemoryInfo()
            manager.getMemoryInfo(info)
            row.put("stage", stage).put("turn", turn)
                .put("sampledPeakPssKb", sampler.peakPssKb.get()).put("sampledPeakNativeHeapBytes", sampler.peakNativeHeapBytes.get()).put("gpuMemory", "NOT_VISIBLE")
                .put("ramTotalBytes", info.totalMem).put("ramAvailableBytes", info.availMem)
                .put("platformLowMemory", info.lowMemory)
                .put("processPssKb", android.os.Debug.MemoryInfo().also {
                    android.os.Debug.getMemoryInfo(it)
                }.totalPss)
                .put("nativeHeapAllocatedBytes", android.os.Debug.getNativeHeapAllocatedSize())
            output.appendText(row.toString() + "\n")
            Log.i("HALO_MODEL_ENDURANCE", row.toString())
        }
        val runner = LiteRtControlProbeRunner(instrumentation.targetContext, graph.modelManager,
            { configuration }, { graph.nativeResourceCoordinator.snapshot.value },
            { provider.modelSession.loadedModelId }, provider.nativeAttemptJournal)
        record("PRE_INIT", 0)
        val result = runner.runResidentEndurance(modelId, turns) { turn, turnResult ->
            row.put("turnPassed", turnResult.controlProbePassed)
                .put("turnResult", turnResult.summaryLine())
                .put("turnStages", turnResult.stages.joinToString("\n") {
                    "${it.stage}: ${it.status}; ${it.durationMs}ms; ${it.boundedDetail.orEmpty()}"
                })
                .put("turnDurationMs", turnResult.totalDurationMs)
            record("CONVERSATION_CLOSED", turn)
        }
        row.put("endurancePassed", result.passed)
            .put("completedTurns", result.passedTurns)
            .put("attemptedTurns", result.attemptedTurns)
            .put("summary", result.summaryLine())
        record("ENGINE_CLOSED", result.attemptedTurns)
        if (!args.getString("captureOnly").equals("true", ignoreCase = true))
            assertTrue("Resident-engine endurance failed: ${result.summaryLine()}", result.passed)
        } finally { sampler.close() }
    }
    @Test fun diagnoseInstalledModel() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val modelId = requireNotNull(args.getString("modelId"))
        val backend = LocalComputeBackend.valueOf(requireNotNull(args.getString("backend")))
        val graph = ManualDependencyGraph.providerGraph
        val provider = graph.providers.inference as LiteRtLocalProvider
        val descriptor = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        requireSelectedInstalledArtifact(descriptor, args)
        assertTrue("Unload selected model before native inspection", provider.unloadLocalModel())
        val sampler = NativeFootprintSampler(instrumentation.targetContext, modelId, descriptor.sha256, args.getString("sourceStamp"), args.getString("runId"))
        try {
        val row = JSONObject().put("modelId", modelId).put("name", descriptor.displayName)
            .put("sha256", descriptor.sha256).put("bytes", descriptor.bytes).put("backend", backend.name)
            .put("device", android.os.Build.MODEL).put("runtime", LiteRtLocalProvider.RUNTIME_VERSION)
            .put("sourceStamp", args.getString("sourceStamp")).put("runId", args.getString("runId"))
        val output = File(instrumentation.targetContext.filesDir, "downloaded-model-diagnostics.jsonl")
        val activityManager = instrumentation.targetContext.getSystemService(android.app.ActivityManager::class.java)
        fun record(stage: String) {
            val memory = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memory)
            row.put("stage", stage)
                .put("sampledPeakPssKb", sampler.peakPssKb.get()).put("sampledPeakNativeHeapBytes", sampler.peakNativeHeapBytes.get()).put("gpuMemory", "NOT_VISIBLE")
                .put("ramTotalBytes", memory.totalMem)
                .put("ramAvailableBytes", memory.availMem)
                .put("platformLowMemory", memory.lowMemory)
                .put("processPssKb", android.os.Debug.MemoryInfo().also { android.os.Debug.getMemoryInfo(it) }.totalPss)
                .put("nativeHeapAllocatedBytes", android.os.Debug.getNativeHeapAllocatedSize())
            output.appendText(row.toString()+"\n")
            Log.i("HALO_MODEL_ACCEPTANCE", row.toString())
        }
        record("ARTIFACT_VERIFIED")
        ModelInfo.from(descriptor.path).use { info ->
            require(info is ModelInfo.Llm) { "Installed model task is non-generative; metadata inspection only" }
            row.put("metadataType", info.modelType.name).put("maxContext", info.maxContextTokens())
                .put("minimumRuntime", info.minRuntimeVersion())
                .put("textBackends", info.supportedBackends(Modality.TEXT).joinToString { it.name })
                .put("modalities", info.inputModalities().toString())
            if (info is ModelInfo.Llm) row.put("nativeThinking", info.supportsThinking())
                .put("nativeFunctionCalling", info.supportsFunctionCalling())
            record("METADATA_READ")
        }
        assertTrue(provider.unloadLocalModel())
        val profile = provider.loadConfigFor(descriptor)
        row.put("profileRequestedContext", if (profile.useModelDefaultContext) "MODEL_DEFAULT" else profile.contextSize)
        val config = profile.copy(computeBackend=backend).let { base ->
            args.getString("contextTokens")?.toIntOrNull()?.let { base.copy(contextSize=it,useModelDefaultContext=false) } ?: base
        }
        row.put("requestedContext", if(config.useModelDefaultContext) "MODEL_DEFAULT" else config.contextSize)
        val cycles = args.getString("cycles")?.toIntOrNull() ?: 1
        require(cycles in 1..5) { "Use 1..5 explicit fresh-engine cycles" }
        row.put("requestedCycles", cycles)
        val probe = LiteRtControlProbeRunner(instrumentation.targetContext, graph.modelManager, { config },
            { graph.nativeResourceCoordinator.snapshot.value }, { provider.modelSession.loadedModelId }, provider.nativeAttemptJournal)
        val outcomes = mutableListOf<com.halo.providers.litert.runtime.LiteRtControlProbeResult>()
        for (index in 0 until cycles) {
            row.put("cycle", index + 1)
            record("RAW_PROBE_STARTED")
            val rawCycle = probe.run(modelId)
            outcomes += rawCycle
            row.put("rawPassed", outcomes.all { it.controlProbePassed })
                .put("rawSummary", rawCycle.summaryLine())
                .put("rawStages", rawCycle.stages.joinToString("\n") { "${it.stage}: ${it.status}; ${it.durationMs}ms; ${it.boundedDetail.orEmpty()}" })
            record("RAW_PROBE_FINISHED")
            if (!rawCycle.controlProbePassed) break
        }
        val raw = outcomes.last()
        val rawPassed = outcomes.size == cycles && outcomes.all { it.controlProbePassed }
        val verifyProvider = args.getString("contextTokens") == null || args.getString("verifyProvider") == "true"
        row.put("completedCycles", outcomes.size).put("rawPassed", rawPassed)
        if (rawPassed && verifyProvider) {
            try {
                record("PROVIDER_VERIFICATION_STARTED")
                val verification = provider.verifyModel(modelId, config)
                row.put("providerVerification", verification.toString())
                    .put("providerPassed", verification is ModelVerificationResult.Verified)
                record("PROVIDER_VERIFICATION_FINISHED")
            } finally {
                provider.unloadLocalModel()
            }
        }
        // The evidence-first matrix can deliberately collect failures for comparison, but
        // normal instrumentation MUST NOT print OK (1 test) when native generation failed.
        // Pass -e captureOnly true explicitly for inventory discovery; it never qualifies GPU.
        record("COMPLETED")
        val captureOnly = args.getString("captureOnly").equals("true", ignoreCase = true)
        if (!captureOnly) {
            assertTrue("Raw model generation failed during ${outcomes.size}/$cycles fresh-engine cycles: ${raw.summaryLine()}", rawPassed)
            if (verifyProvider) {
                assertTrue("Halo provider verification failed or was skipped: ${row.optString("providerVerification")}",
                    row.optBoolean("providerPassed", false))
            }
        }
        } finally { sampler.close() }
    }
}
