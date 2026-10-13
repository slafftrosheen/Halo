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
    @Test fun diagnoseInstalledModel() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val modelId = requireNotNull(args.getString("modelId"))
        val backend = LocalComputeBackend.valueOf(requireNotNull(args.getString("backend")))
        val graph = ManualDependencyGraph.providerGraph
        val provider = graph.providers.inference as LiteRtLocalProvider
        val descriptor = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        val row = JSONObject().put("modelId", modelId).put("name", descriptor.displayName)
            .put("sha256", descriptor.sha256).put("bytes", descriptor.bytes).put("backend", backend.name)
            .put("device", android.os.Build.MODEL).put("runtime", LiteRtLocalProvider.RUNTIME_VERSION)
        val output = File(instrumentation.targetContext.filesDir, "downloaded-model-diagnostics.jsonl")
        val activityManager = instrumentation.targetContext.getSystemService(android.app.ActivityManager::class.java)
        fun record(stage: String) {
            val memory = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memory)
            row.put("stage", stage)
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
            row.put("metadataType", info.modelType.name).put("maxContext", info.maxContextTokens())
                .put("minimumRuntime", info.minRuntimeVersion())
                .put("textBackends", info.supportedBackends(Modality.TEXT).joinToString { it.name })
                .put("modalities", info.inputModalities().toString())
            if (info is ModelInfo.Llm) row.put("nativeThinking", info.supportsThinking())
                .put("nativeFunctionCalling", info.supportsFunctionCalling())
            record("METADATA_READ")
        }
        assertTrue(provider.unloadLocalModel())
        val config = provider.loadConfigFor(descriptor).copy(computeBackend=backend).let { base ->
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
        row.put("completedCycles", outcomes.size).put("rawPassed", rawPassed)
        if (rawPassed && args.getString("contextTokens") == null) {
            val preferences = LiteRtModelPreferences(ManualDependencyGraph.storage)
            val previous = preferences.developerBackend(modelId)
            try {
                provider.setDeveloperBackend(modelId,backend)
                record("PROVIDER_VERIFICATION_STARTED")
                val verification = provider.verifyModel(modelId)
                row.put("providerVerification", verification.toString())
                    .put("providerPassed", verification is ModelVerificationResult.Verified)
                record("PROVIDER_VERIFICATION_FINISHED")
            } finally {
                provider.unloadLocalModel()
                if(previous == null) preferences.clearDeveloperBackend(modelId) else preferences.setDeveloperBackend(modelId,previous)
            }
        }
        // The evidence-first matrix can deliberately collect failures for comparison, but
        // normal instrumentation MUST NOT print OK (1 test) when native generation failed.
        // Pass -e captureOnly true explicitly for inventory discovery; it never qualifies GPU.
        record("COMPLETED")
        val captureOnly = args.getString("captureOnly").equals("true", ignoreCase = true)
        if (!captureOnly) {
            assertTrue("Raw model generation failed during ${outcomes.size}/$cycles fresh-engine cycles: ${raw.summaryLine()}", rawPassed)
            if (args.getString("contextTokens") == null) {
                assertTrue("Halo provider verification failed or was skipped: ${row.optString("providerVerification")}",
                    row.optBoolean("providerPassed", false))
            }
        }
    }
}
