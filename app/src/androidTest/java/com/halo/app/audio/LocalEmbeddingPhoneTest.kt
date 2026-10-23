package com.halo.app.audio

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.halo.app.di.ManualDependencyGraph
import com.halo.providers.litert.EmbeddingUnavailableException
import com.halo.providers.litert.LiteRtEmbeddingProvider
import com.halo.providers.litert.LiteRtLocalProvider
import com.halo.providers.litert.LocalEmbeddingInput
import com.halo.providers.litert.LocalMediaEmbeddingRequest
import com.halo.providers.litert.LocalMediaEmbeddingSafety
import com.halo.sdk.provider.EmbeddingBatch
import com.halo.sdk.provider.EmbeddingRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

/** Explicit synthetic CPU diagnostic; never promotes capabilities or changes saved selection. */
@RunWith(AndroidJUnit4::class)
class LocalEmbeddingPhoneTest {
    @Test fun qualifyInstalledEmbedding() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val graph = ManualDependencyGraph.providerGraph
        val modelId = requireNotNull(args.getString("modelId"))
        require(modelId == "embeddinggemma2-740m") { "This fixture targets the exact EmbeddingGemma 2 catalogue artifact" }
        require(args.getString("backend") == "CPU")
        val artifact = requireNotNull(graph.modelManager.verifyPhysicalArtifact(modelId))
        requireSelectedInstalledArtifact(artifact, args)
        require(artifact.valid)
        val sha = requireNotNull(artifact.sha256)
        require(sha == graph.modelManager.catalogModel(modelId)?.sha256) { "Fixture requires the supported catalogue fingerprint" }
        val provider = LiteRtEmbeddingProvider(graph.modelManager, graph.nativeResourceCoordinator, modelId)
        val output = File(context.filesDir, "local-embedding-evidence.jsonl")
        fun record(stage: String, status: String, detail: JSONObject = JSONObject()) {
            output.appendText(detail.put("stage", stage).put("status", status)
                .put("fixture", "synthetic-embedding-cpu-v1").put("modelId", modelId)
                .put("sha256", sha).put("bytes", artifact.bytes).put("backend", "CPU")
                .put("dimension", 768).put("runtime", LiteRtLocalProvider.RUNTIME_VERSION)
                .put("device", Build.MODEL).put("api", Build.VERSION.SDK_INT)
                .put("runId", args.getString("runId")).put("sourceStamp", args.getString("sourceStamp"))
                .put("atMs", System.currentTimeMillis()).put("selectionEvidenceSaved", false).toString() + "\n")
        }
        val sampler = NativeFootprintSampler(context, modelId, sha, args.getString("sourceStamp"), args.getString("runId"))
        suspend fun probe(stage: String, execute: suspend () -> JSONObject): String {
            record(stage, "STARTED")
            val started = SystemClock.elapsedRealtime()
            return try {
                // Cooperative only: the host runner bounds synchronous JNI and preserves partial rows.
                val result = withTimeout(180_000L) { execute() }
                val passed = result.getBoolean("passed")
                provider.metrics.value?.let {
                    result.put("initMs", it.initMs).put("computationMs", it.generationMs)
                        .put("batchSize", it.batchSize).put("admissionEstimateBytes", it.estimatedMemoryBytes)
                }
                val status = if (passed) "PASSED" else "QUALITY_FAILED"
                record(stage, status,
                    result.put("elapsedMs", SystemClock.elapsedRealtime() - started))
                status
            } catch (cancelled: CancellationException) {
                record(stage, "INTERRUPTED", JSONObject().put("elapsedMs", SystemClock.elapsedRealtime() - started))
                throw cancelled
            } catch (failure: Exception) {
                // Unsupported native/backend input is not a failed semantic ranking or a pass.
                record(stage, "UNAVAILABLE", JSONObject()
                    .put("reasonCode", (failure as? EmbeddingUnavailableException)?.reasonCode ?: failure.javaClass.simpleName)
                    .put("elapsedMs", SystemClock.elapsedRealtime() - started))
                "UNAVAILABLE"
            }
        }
        try {
            val textStatus = probe("TEXT_RETRIEVAL_EN_LV_RU_PL") {
                val documents = listOf(
                    "title: Cat care | text: A kitten needs fresh water, cat food and a clean litter tray.",
                    "title: Bread recipe | text: Mix flour, yeast and water, let the dough rise, then bake the bread.",
                    "title: Astronomy | text: Stars and planets can be observed through a telescope at night.")
                val queries = listOf("EN" to "How should I care for a kitten?",
                    "LV" to "Kā rūpēties par kaķēnu?", "RU" to "Как ухаживать за котёнком?",
                    "PL" to "Jak opiekować się kotkiem?")
                val batch = provider.embed(EmbeddingRequest(documents + queries.map { "task: search result | query: ${it.second}" },
                    sha, 768, "embeddinggemma2-search-asymmetric-v1"))
                val scores = JSONArray()
                val pass = queries.indices.map { index ->
                    val similarities = documents.indices.map { cosine(batch, index + documents.size, it) }
                    val margin = similarities[0] - similarities.drop(1).max()
                    scores.put(JSONObject().put("language", queries[index].first)
                        .put("expectedDocument", 0).put("scores", JSONArray(similarities))
                        .put("margin", margin).put("passed", margin > 0.01))
                    margin > 0.01
                }.all { it }
                JSONObject().put("passed", pass).put("minimumMargin", 0.01).put("cases", scores)
            }
            val imageStatus = probe("SYNTHETIC_IMAGE_RETRIEVAL") {
                val redCircle = image(circle = true)
                val blueSquare = image(circle = false)
                val batch = provider.embedMedia(LocalMediaEmbeddingRequest(sha, 768, listOf(
                    LocalEmbeddingInput.Image(redCircle), LocalEmbeddingInput.Image(blueSquare),
                    LocalEmbeddingInput.Image(redCircle),
                    LocalEmbeddingInput.Text("task: search result | query: a red circle on a white background"),
                    LocalEmbeddingInput.Text("task: search result | query: a blue square on a white background")),
                    LocalMediaEmbeddingSafety.CROSS_MODAL_IMAGE_SEARCH_V1))
                val redMargin = cosine(batch, 3, 0) - cosine(batch, 3, 1)
                val blueMargin = cosine(batch, 4, 1) - cosine(batch, 4, 0)
                val duplicate = cosine(batch, 0, 2)
                val imageMargin = duplicate - cosine(batch, 0, 1)
                JSONObject().put("passed", redMargin > 0.01 && blueMargin > 0.01 && duplicate >= 0.999 && imageMargin > 0.01)
                    .put("minimumMargin", 0.01).put("minimumDuplicateCosine", 0.999)
                    .put("redTextMargin", redMargin).put("blueTextMargin", blueMargin)
                    .put("duplicateImageCosine", duplicate).put("imageDiscriminationMargin", imageMargin)
            }
            val passed = textStatus == "PASSED" && imageStatus == "PASSED"
            record("SUMMARY", if (passed) "PASSED" else "NOT_QUALIFIED",
                JSONObject().put("textStatus", textStatus).put("imageStatus", imageStatus))
            assertTrue("EMBEDDING_OUTCOME run=${args.getString("runId")} sha=$sha text=$textStatus image=$imageStatus", passed)
        } finally {
            sampler.close()
            record("SAMPLED_FOOTPRINT", "RECORDED", JSONObject()
                .put("sampledPeakPssKb", sampler.peakPssKb.get())
                .put("sampledPeakNativeHeapBytes", sampler.peakNativeHeapBytes.get())
                .put("sampleIntervalMs", 200).put("gpuMemory", "NOT_VISIBLE"))
            // execute() retains ownership when teardown fails. Do not unregister or release pins here.
        }
    }

    private fun cosine(batch: EmbeddingBatch, a: Int, b: Int): Double =
        batch.vectors[a].indices.sumOf { batch.vectors[a][it].toDouble() * batch.vectors[b][it] }

    private fun image(circle: Boolean): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888)
        return try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (circle) Color.RED else Color.BLUE }
            if (circle) canvas.drawCircle(128f, 128f, 80f, paint)
            else canvas.drawRect(48f, 48f, 208f, 208f, paint)
            ByteArrayOutputStream().use { bytes ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes))
                bytes.toByteArray()
            }
        } finally { bitmap.recycle() }
    }
}
