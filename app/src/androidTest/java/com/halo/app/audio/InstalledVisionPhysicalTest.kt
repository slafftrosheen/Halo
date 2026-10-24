package com.halo.app.audio

import android.app.ActivityManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.ai.edge.litertlm.ModelInfo
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.runtime.*
import com.halo.sdk.provider.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Explicit installed-artifact diagnostics. No role assignments, tools, downloads or private prompts. */
@RunWith(AndroidJUnit4::class)
class InstalledVisionPhysicalTest {
    @Test fun explicitGpuSyntheticImage() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        require(args.getString("allowGpu") == "true") { "Explicit operator GPU approval required" }
        require(args.getString("cpuAndGpuProofChecked") == "true") { "Host must validate matching artifact/app CPU and GPU evidence" }
        val modelId = requireNotNull(args.getString("modelId"))
        val graph = ManualDependencyGraph.providerGraph
        val descriptor = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        requireSelectedInstalledArtifact(descriptor, args)
        ModelInfo.from(descriptor.path).use { info ->
            require(info is ModelInfo.Llm && info.inputModalities().vision)
        }
        val memory = ActivityManager.MemoryInfo()
        instrumentation.targetContext.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        require(memory.availMem >= descriptor.bytes + 1024L * 1024 * 1024)
        val provider = graph.providers.inference as LiteRtLocalProvider
        assertTrue(provider.unloadLocalModel())
        val row = JSONObject().put("modelId", modelId).put("sha256", descriptor.sha256)
            .put("sourceStamp", args.getString("sourceStamp")).put("runId", args.getString("runId"))
            .put("requestedBackend", "GPU").put("requestedContext", 1024).put("stage", "ENGINE_INIT")
        try {
            val load = provider.modelSession.loadModel(modelId, descriptor.path,
                config = RuntimeLoadConfig(contextSize = 1024, computeBackend = LocalComputeBackend.GPU, visionEnabled = true),
                artifactSha256 = descriptor.sha256).getOrThrow()
            row.put("effectiveBackend", load.activeBackend.name).put("effectiveContext", load.effectiveContextSize)
            require(load.activeBackend == LocalComputeBackend.GPU)
            row.put("stage", "GENERATION")
            val result = withTimeout(120_000) {
                provider.modelSession.generate(RuntimeGenerationConfig(prompt = "Read the text and number in this image.",
                    imageBytes = syntheticImage(), temperature = 0.0, topK = 1, maxTokens = 256))
            }.getOrThrow()
            val matched = Regex("\\bHALO\\b", RegexOption.IGNORE_CASE).containsMatchIn(result.text) &&
                Regex("\\b42\\b").containsMatchIn(result.text)
            row.put("stage", "COMPLETED").put("matched", matched).put("nonempty", result.text.isNotBlank())
                .put("fixedFixtureOutput", result.text.take(256)).put("status", if (matched) "PASSED" else "ANSWER_MISMATCH")
        } catch (error: Exception) {
            row.put("status", "FAILED").put("exceptionType", error.javaClass.simpleName)
        } finally {
            row.put("engineUnloaded", provider.unloadLocalModel())
            File(instrumentation.targetContext.filesDir, "installed-vision-acceptance.jsonl").appendText(row.toString() + "\n")
        }
        assertTrue("Synthetic image fixture failed; see bounded report", row.optString("status") == "PASSED")
    }
    private fun syntheticImage(): ByteArray {
        val bitmap = android.graphics.Bitmap.createBitmap(256, 160, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK; textSize = 44f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            canvas.drawText("HALO", 20f, 65f, paint)
            canvas.drawText("42", 20f, 125f, paint)
            return java.io.ByteArrayOutputStream().use { out ->
                check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out))
                out.toByteArray()
            }
        } finally { bitmap.recycle() }
    }

}
