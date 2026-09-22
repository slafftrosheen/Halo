package com.halo.app.models

import android.app.ActivityManager
import android.content.Context
import com.halo.providers.llamacpp.LocalModelMemoryPlanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.time.Instant
import java.util.concurrent.TimeUnit

enum class GgufQuantization {
    Q2_K, Q3_K_S, Q3_K_M, Q3_K_L, Q4_0, Q4_K_S, Q4_K_M,
    Q5_K_S, Q5_K_M, Q6_K, Q8_0, IQ2, IQ3, IQ4, UNKNOWN
}

fun parseQuantization(filename: String): GgufQuantization {
    val normalized = filename.uppercase()
    val exact = listOf("Q3_K_S", "Q3_K_M", "Q3_K_L", "Q4_K_S", "Q4_K_M",
        "Q5_K_S", "Q5_K_M", "Q2_K", "Q4_0", "Q6_K", "Q8_0")
    return exact.firstOrNull { Regex("(^|[^A-Z0-9])${Regex.escape(it)}([^A-Z0-9]|$)")
        .containsMatchIn(normalized) }?.let(GgufQuantization::valueOf)
        ?: when {
            Regex("(^|[^A-Z0-9])IQ2[A-Z0-9_]*").containsMatchIn(normalized) -> GgufQuantization.IQ2
            Regex("(^|[^A-Z0-9])IQ3[A-Z0-9_]*").containsMatchIn(normalized) -> GgufQuantization.IQ3
            Regex("(^|[^A-Z0-9])IQ4[A-Z0-9_]*").containsMatchIn(normalized) -> GgufQuantization.IQ4
            else -> GgufQuantization.UNKNOWN
        }
}

data class ModelArtifactIdentity(val repoId: String, val revisionSha: String, val filename: String) {
    val url: String get() = "https://huggingface.co/$repoId/resolve/$revisionSha/" +
        filename.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
}

data class RemoteGgufFile(
    val filename: String,
    val bytes: Long,
    val quantization: GgufQuantization,
    val split: Boolean = false
)

data class RemoteGgufModel(
    val repoId: String,
    val revision: String,
    val author: String?,
    val pipelineTag: String?,
    val tags: Set<String>,
    val downloads: Long?,
    val likes: Int?,
    val lastModified: Instant?,
    val license: String?,
    val gated: Boolean,
    val files: List<RemoteGgufFile>
)

data class ModelCatalogEntry(
    val model: RemoteGgufModel,
    val file: RemoteGgufFile,
    val fit: LocalModelMemoryPlanner.RemoteModelFit,
    val score: Int
) {
    val artifact: ModelArtifactIdentity get() =
        ModelArtifactIdentity(model.repoId, model.revision, file.filename)
    val isInstructionModel: Boolean get() = isInstruction(model.repoId, model.tags)
}

sealed interface ModelCatalogState {
    data object Idle : ModelCatalogState
    data object Refreshing : ModelCatalogState
    data class Ready(val entries: List<ModelCatalogEntry>, val updatedAt: Long) : ModelCatalogState
    data class OfflineCache(val entries: List<ModelCatalogEntry>, val updatedAt: Long) : ModelCatalogState
    data class Failed(val reason: String) : ModelCatalogState
}

interface RemoteModelCatalog {
    val state: StateFlow<ModelCatalogState>
    suspend fun refresh(force: Boolean = false)
    suspend fun search(query: String): List<ModelCatalogEntry>
}

class HuggingFaceGgufCatalog(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS).build()
) : RemoteModelCatalog {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("halo_gguf_catalog", Context.MODE_PRIVATE)
    private val mutableState = MutableStateFlow<ModelCatalogState>(ModelCatalogState.Idle)
    override val state: StateFlow<ModelCatalogState> = mutableState

    override suspend fun refresh(force: Boolean) = withContext(Dispatchers.IO) {
        val cached = readCache()
        if (cached != null) mutableState.value = ModelCatalogState.OfflineCache(cached.first, cached.second)
        if (!force && cached != null && System.currentTimeMillis() - cached.second < 6 * 60 * 60 * 1000L) {
            mutableState.value = ModelCatalogState.Ready(cached.first, cached.second)
            return@withContext
        }
        mutableState.value = ModelCatalogState.Refreshing
        try {
            val url = "https://huggingface.co/api/models?filter=gguf&pipeline_tag=text-generation" +
                "&sort=trendingScore&direction=-1&limit=100&expand=sha&expand=siblings"
            val request = Request.Builder().url(url)
                .header("Accept", "application/json")
                .apply { if (cached != null) prefs.getString("etag", null)?.let { header("If-None-Match", it) } }
                .build()
            val response = client.newCall(request).execute()
            val listing = response.use {
                if (it.code == 304 && cached != null) {
                    mutableState.value = ModelCatalogState.Ready(cached.first, cached.second)
                    return@withContext
                }
                if (!it.isSuccessful) throw IOException("Hub returned HTTP ${it.code}")
                val body = it.body?.string() ?: throw IOException("Empty Hub response")
                it.header("ETag")?.let { etag -> prefs.edit().putString("etag", etag).apply() }
                JSONArray(body)
            }
            val semaphore = Semaphore(5)
            val models = coroutineScope {
                (0 until listing.length()).map { index ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val summary = listing.optJSONObject(index) ?: return@withPermit null
                            val repoId = summary.optString("id")
                            val sha = summary.optString("sha")
                            if (repoId.isBlank() || !Regex("[0-9a-f]{40}").matches(sha) ||
                                !summary.hasGgufSibling()) return@withPermit null
                            runCatching { fetchModel(repoId, sha) }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull()
            }
            val memory = memorySnapshot()
            val entries = models.flatMap { model ->
                model.files.map { file ->
                    val fit = LocalModelMemoryPlanner.preflightRemote(file.bytes, memory)
                    ModelCatalogEntry(model, file, fit, score(model, file, fit))
                }
            }.sortedWith(compareByDescending<ModelCatalogEntry> { it.score }
                .thenBy { it.model.repoId }.thenBy { it.file.filename })
            if (entries.isEmpty()) throw IOException("No compatible GGUF files in Hub response")
            val updatedAt = System.currentTimeMillis()
            saveCache(entries, updatedAt)
            mutableState.value = ModelCatalogState.Ready(entries, updatedAt)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            mutableState.value = cached?.let { ModelCatalogState.OfflineCache(it.first, it.second) }
                ?: ModelCatalogState.Failed(error.message ?: "Catalog unavailable")
        }
    }

    override suspend fun search(query: String): List<ModelCatalogEntry> {
        val entries = when (val snapshot = state.value) {
            is ModelCatalogState.Ready -> snapshot.entries
            is ModelCatalogState.OfflineCache -> snapshot.entries
            else -> emptyList()
        }
        val text = query.trim()
        return if (text.isBlank()) entries else entries.filter {
            it.model.repoId.contains(text, true) || it.file.filename.contains(text, true)
        }
    }

    private fun fetchModel(repoId: String, sha: String): RemoteGgufModel? {
        val encodedRepo = repoId.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        val request = Request.Builder()
            .url("https://huggingface.co/api/models/$encodedRepo/revision/$sha?blobs=true")
            .header("Accept", "application/json").build()
        val detail = client.newCall(request).execute().use {
            if (!it.isSuccessful) return null
            JSONObject(it.body?.string() ?: return null)
        }
        if (detail.optString("sha") != sha) return null
        val pipelineTag = detail.optString("pipeline_tag").ifBlank { "text-generation" }
        val gatedValue = detail.opt("gated")
        val gated = gatedValue == true || (gatedValue is String && gatedValue != "false")
        if (gated || detail.optBoolean("private", false)) return null
        val siblings = detail.optJSONArray("siblings") ?: return null
        val names = (0 until siblings.length()).mapNotNull { siblings.optJSONObject(it)?.optString("rfilename") }
        if (names.any { it.contains("mmproj", true) || it.contains("vision", true) ||
                it.contains("projector", true) }) return null
        val files = (0 until siblings.length()).mapNotNull { index ->
            val sibling = siblings.optJSONObject(index) ?: return@mapNotNull null
            val filename = sibling.optString("rfilename")
            val bytes = sibling.optLong("size", -1L)
            if (!filename.endsWith(".gguf", true) || bytes <= 0L ||
                Regex("-\\d{5}-of-\\d{5}\\.gguf$", RegexOption.IGNORE_CASE).containsMatchIn(filename)) {
                return@mapNotNull null
            }
            RemoteGgufFile(filename, bytes, parseQuantization(filename))
        }
        if (files.isEmpty()) return null
        val tags = detail.optJSONArray("tags")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
        }.orEmpty()
        return RemoteGgufModel(
            repoId = repoId, revision = sha, author = detail.optString("author").takeIf(String::isNotBlank),
            pipelineTag = pipelineTag, tags = tags,
            downloads = detail.optLong("downloads").takeIf { it > 0 },
            likes = detail.optInt("likes").takeIf { it > 0 },
            lastModified = runCatching { Instant.parse(detail.optString("lastModified")) }.getOrNull(),
            license = detail.optJSONObject("cardData")?.optString("license")?.takeIf(String::isNotBlank),
            gated = false, files = files
        )
    }

    private fun JSONObject.hasGgufSibling(): Boolean {
        val siblings = optJSONArray("siblings") ?: return false
        return (0 until siblings.length()).any {
            siblings.optJSONObject(it)?.optString("rfilename")?.endsWith(".gguf", true) == true
        }
    }

    private fun memorySnapshot(): LocalModelMemoryPlanner.MemorySnapshot {
        val info = ActivityManager.MemoryInfo()
        (appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        return LocalModelMemoryPlanner.MemorySnapshot(info.totalMem, info.availMem, info.lowMemory)
    }

    private fun score(model: RemoteGgufModel, file: RemoteGgufFile,
                      fit: LocalModelMemoryPlanner.RemoteModelFit): Int {
        val fitScore = when (fit) {
            is LocalModelMemoryPlanner.RemoteModelFit.Recommended -> 4
            is LocalModelMemoryPlanner.RemoteModelFit.Fits -> 3
            is LocalModelMemoryPlanner.RemoteModelFit.Tight -> 2
            is LocalModelMemoryPlanner.RemoteModelFit.TooLarge -> 0
            LocalModelMemoryPlanner.RemoteModelFit.Unknown -> 1
        }
        val quantScore = when (file.quantization) {
            GgufQuantization.Q4_K_M -> 7
            GgufQuantization.Q4_K_S -> 6
            GgufQuantization.Q5_K_M -> 5
            GgufQuantization.Q5_K_S -> 4
            GgufQuantization.Q3_K_M -> 3
            GgufQuantization.Q6_K -> 2
            GgufQuantization.Q8_0 -> 1
            else -> 0
        }
        return fitScore * 100 + (if (isInstruction(model.repoId, model.tags)) 30 else 0) +
            quantScore * 2 + (model.downloads ?: 0L).coerceAtMost(1_000_000L).div(100_000L).toInt()
    }

    private fun readCache(): Pair<List<ModelCatalogEntry>, Long>? {
        return runCatching {
        val raw = prefs.getString("entries", null) ?: return null
        val root = JSONObject(raw)
        val updatedAt = root.getLong("updatedAt")
        val array = root.getJSONArray("items")
        val memory = memorySnapshot()
        val entries = (0 until array.length()).mapNotNull { index ->
            val row = array.optJSONObject(index) ?: return@mapNotNull null
            val model = RemoteGgufModel(
                row.getString("repo"), row.getString("sha"), row.optString("author").takeIf(String::isNotBlank),
                "text-generation", row.optString("tags").split('|').filter(String::isNotBlank).toSet(),
                row.optLong("downloads"), row.optInt("likes"),
                runCatching { Instant.parse(row.optString("modified")) }.getOrNull(),
                row.optString("license").takeIf(String::isNotBlank), false, emptyList()
            )
            val file = RemoteGgufFile(row.getString("file"), row.getLong("bytes"),
                parseQuantization(row.getString("file")))
            val fit = LocalModelMemoryPlanner.preflightRemote(file.bytes, memory)
            ModelCatalogEntry(model.copy(files = listOf(file)), file, fit, score(model, file, fit))
        }.sortedByDescending { it.score }
        entries to updatedAt
        }.getOrNull()
    }

    private fun saveCache(entries: List<ModelCatalogEntry>, updatedAt: Long) {
        val items = JSONArray()
        entries.forEach { entry ->
            val model = entry.model
            items.put(JSONObject().put("repo", model.repoId).put("sha", model.revision)
                .put("author", model.author).put("tags", model.tags.joinToString("|"))
                .put("downloads", model.downloads).put("likes", model.likes)
                .put("modified", model.lastModified?.toString()).put("license", model.license)
                .put("file", entry.file.filename).put("bytes", entry.file.bytes))
        }
        prefs.edit().putString("entries", JSONObject().put("updatedAt", updatedAt)
            .put("items", items).toString()).apply()
    }
}

private fun isInstruction(repoId: String, tags: Set<String>): Boolean {
    val text = "$repoId ${tags.joinToString(" ")}".lowercase()
    return listOf("instruct", "chat", "assistant", "-it", "_it").any { it in text }
}
