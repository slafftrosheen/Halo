package com.halo.providers.litert.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Hub metadata is discovery, never native compatibility or generation evidence. */
class HuggingFaceLiteRtCatalog(private val client: OkHttpClient = OkHttpClient.Builder()
    .callTimeout(30, TimeUnit.SECONDS).build(),
    private val endpoint: String = "https://huggingface.co") {
    suspend fun discover(query: String): List<LiteRtCatalogModel> = withContext(Dispatchers.IO) {
        // Parse the fixed/test base rather than letting search text become a URL.
        val base = endpoint.trimEnd('/')
        val encoded = java.net.URLEncoder.encode(query.take(100), "UTF-8")
        val results = org.json.JSONArray(get("$base/api/models?author=litert-community&search=$encoded&limit=60&sort=downloads&direction=-1"))
        val models = mutableListOf<LiteRtCatalogModel>()
        for (i in 0 until results.length()) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            val repo = results.getJSONObject(i).getString("id")
            if (!Regex("litert-community/[A-Za-z0-9._-]+").matches(repo)) continue
            val details = try { JSONObject(get("$base/api/models/$repo?blobs=true")) }
                catch(cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch(_: Exception) { continue }
            // LiteRT 0.18 also ships embedding artifacts; classify separately instead of
            // either hiding them or falsely advertising them as chat models.
            models += project(repo, details)
        }
        check(models.isNotEmpty()) { "No pinned models discovered" }
        models.take(100)
    }
    private suspend fun get(url: String): String {
        val call=client.newCall(Request.Builder().url(url).build())
        val response=kotlinx.coroutines.suspendCancellableCoroutine<okhttp3.Response> { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object: okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) { if(continuation.isActive) continuation.resumeWith(Result.failure(e)) }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }
            })
        }
        val watcher=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.currentCoroutineContext()).launch(start=kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            try { kotlinx.coroutines.awaitCancellation() } finally { call.cancel() }
        }
        return try { response.use {
            check(it.isSuccessful) { "Hub metadata unavailable (${it.code})" }
            val body=it.body ?: error("Empty Hub metadata")
            body.byteStream().use { stream ->
                val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while(true) { kotlinx.coroutines.currentCoroutineContext().ensureActive(); val n=stream.read(buffer); if(n<0) break
                    check(output.size()+n <= 2*1024*1024) { "Hub metadata too large" }; output.write(buffer,0,n) }
                output.toString("UTF-8")
            }
        } } finally { watcher.cancel(); call.cancel() }
    }
    companion object {
        fun project(repo: String, details: JSONObject): List<LiteRtCatalogModel> {
            if(!Regex("litert-community/[A-Za-z0-9._-]+").matches(repo)) return emptyList()
            val revision = details.optString("sha")
            if (!Regex("[0-9a-f]{40}").matches(revision) || details.optBoolean("private")) return emptyList()
            val files = details.optJSONArray("siblings") ?: return emptyList()
            val gated = details.opt("gated").let { it != null && it != false && it != JSONObject.NULL }
            return (0 until files.length()).mapNotNull { index ->
                val file = files.getJSONObject(index)
                val name = file.optString("rfilename")
                // Accelerator-specific binaries are not portable CPU/GPU models.
                if (!Regex("[A-Za-z0-9._-]+\\.litertlm").matches(name) ||
                    Regex("(?i)(mediatek|qualcomm|qnn|npu|mt[0-9]{4})").containsMatchIn(name)) return@mapNotNull null
                // Public Bonsai INT1 is a non-runnable research PoC: no LiteRT INT1 kernel.
                // Do not offer a guaranteed-crashing artifact as a downloadable model.
                if (LiteRtCommunityModelGuide.knownNonRunnable(repo, name)) return@mapNotNull null
                val lfs = file.optJSONObject("lfs") ?: return@mapNotNull null
                val hash = lfs.optString("sha256")
                val bytes = lfs.optLong("size")
                if (!Regex("[0-9a-f]{64}").matches(hash) || bytes <= 1048576 || bytes > 8L*1024*1024*1024) return@mapNotNull null
                val identity = MessageDigest.getInstance("SHA-256").digest("$repo/$name/$hash".toByteArray())
                    .joinToString("") { "%02x".format(it) }.take(24)
                val task = LiteRtArtifactTaskClassifier.fromSource(repo, name, details.optString("pipeline_tag"))
                LiteRtCatalogModel(id="hf-$identity", displayName=name.removeSuffix(".litertlm").replace('_',' '),
                    repoId=repo, revision=revision, filename=name, expectedBytes=bytes, sha256=hash,
                    contextWindowTokens=1024, supportsVision=false, supportsTools=false,
                    minLiteRtLmVersion=if (task == LiteRtArtifactTask.EMBEDDING) "0.18.0" else "0.17.1",
                    task=task,
                    license=details.optJSONObject("cardData")?.optString("license")?.takeIf { it.isNotBlank() },
                    evaluationPurposes=if (task == LiteRtArtifactTask.GENERATION)
                        LocalModelEvaluationPurpose.entries.toSet() else emptySet(),
                    access=if(gated) LiteRtCatalogAccess.GATED_IMPORT_ONLY else LiteRtCatalogAccess.OPEN_DIRECT,
                    toolCallPreference=LiteRtToolCallPreference.NONE,
                    runtimeProfile=LiteRtRuntimeProfile(preferredBackend=com.halo.sdk.provider.LocalComputeBackend.CPU,
                        useModelDefaultContext=true,
                        defaultMaxOutputTokens=if(task == LiteRtArtifactTask.GENERATION) 256 else 1))
            }
        }
        fun encode(models: List<LiteRtCatalogModel>): String = org.json.JSONArray(models.map { m ->
            JSONObject().put("repo",m.repoId).put("sha",m.revision).put("name",m.filename)
                .put("bytes",m.expectedBytes).put("hash",m.sha256).put("gated",m.requiresLicenseAcceptance)
                .put("license",m.license).put("pipeline",when(m.task) {
                    LiteRtArtifactTask.EMBEDDING -> "feature-extraction"
                    LiteRtArtifactTask.NON_GENERATIVE -> "audio-classification"
                    LiteRtArtifactTask.GENERATION -> "text-generation"
                })
        }).toString()
        fun decode(value: String): List<LiteRtCatalogModel> {
            val array=org.json.JSONArray(value)
            require(array.length() <= 1000)
            return (0 until array.length()).flatMap { i ->
                val m=array.getJSONObject(i); val repo=m.getString("repo")
                require(Regex("litert-community/[A-Za-z0-9._-]+").matches(repo))
                project(repo,JSONObject().put("sha",m.getString("sha")).put("gated",m.getBoolean("gated"))
                    .put("pipeline_tag",m.optString("pipeline"))
                    .put("cardData",JSONObject().put("license",m.optString("license").takeIf { it.isNotBlank() }))
                    .put("siblings",org.json.JSONArray().put(JSONObject().put("rfilename",m.getString("name"))
                        .put("lfs",JSONObject().put("sha256",m.getString("hash")).put("size",m.getLong("bytes"))))))
            }
        }
    }
}
