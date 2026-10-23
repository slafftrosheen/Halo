package com.halo.app.audio

import android.app.ActivityManager
import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.providers.litert.LiteRtArtifactInspector
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.litert.LiteRtLocalProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * User-invoked, metadata-only inspection of any installed .litertlm.
 *
 * Embeddings/other workloads MUST NOT be sent to Engine.createConversation() just to
 * discover their task. LiteRT ModelInfo itself can transiently allocate substantial
 * memory for large artifacts (see LiteRT-LM #3878): never run on app startup, or in
 * a batch against every installed model on a low-RAM phone.
 */
@RunWith(AndroidJUnit4::class)
class LocalArtifactInspectionTest {
    @Test fun inspectInstalledArtifact() = runBlocking {
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = instrument.targetContext
        val modelId = requireNotNull(InstrumentationRegistry.getArguments().getString("modelId"))
        val graph = ManualDependencyGraph.providerGraph
        val descriptor = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        requireSelectedInstalledArtifact(descriptor, InstrumentationRegistry.getArguments())
        assertTrue("Artifact is invalid: ${descriptor.validation}", descriptor.valid)

        val output = File(context.filesDir, "local-artifact-inspection.jsonl")
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        val row = JSONObject()
            .put("modelId", modelId)
            .put("sha256", descriptor.sha256)
            .put("bytes", descriptor.bytes)
            .put("validation", descriptor.validation.name)
            .put("androidDevice", Build.MODEL)
            .put("androidVersion", Build.VERSION.RELEASE)
            .put("litertRuntime", LiteRtLocalProvider.RUNTIME_VERSION)
            .put("sourceStamp", InstrumentationRegistry.getArguments().getString("sourceStamp"))
            .put("runId", InstrumentationRegistry.getArguments().getString("runId"))
            .put("ramTotalBytes", memory.totalMem)
            .put("ramAvailableBeforeBytes", memory.availMem)
            .put("nativeHeapBeforeBytes", Debug.getNativeHeapAllocatedSize())
            .put("processPssBeforeKb", Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss)

        val started = System.currentTimeMillis()
        // Use the same pinned native/resource lifetime as production inspection. Never open
        // an independent ModelInfo handle that can race file removal or another native owner.
        val info = LiteRtArtifactInspector(graph.nativeResourceCoordinator, graph.modelManager).inspect(descriptor)
        row.put("modelType", info.task)
            .put("isConversationGenerator", info.task == "LLM")
            .put("textBackends", info.textBackends.joinToString(","))
            .put("nativeVisionDeclared", info.vision)
            .put("nativeFunctionsDeclared", info.nativeTools)
            .put("embeddingDimension", info.embeddingDimension ?: JSONObject.NULL)
            .put("metadataScope", "CANONICAL_INSPECTOR_FIELDS")
        // This owner does not expose complete audio/runtime/context/backend metadata.
        // Missing fields remain unqualified; never infer them from catalogue modalities.
        manager.getMemoryInfo(memory)
        row.put("inspectionMs", System.currentTimeMillis() - started)
            .put("ramAvailableAfterBytes", memory.availMem)
            .put("nativeHeapAfterBytes", Debug.getNativeHeapAllocatedSize())
            .put("processPssAfterKb", Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss)
            .put("stage", "METADATA_ONLY")
        output.appendText(row.toString() + "\n")
        Log.i("HALO_LOCAL_MODEL_INFO", row.toString())
        // No Engine, no Conversation, no model response, no qualification changes.
        assertTrue(row.has("modelType"))
    }
}
