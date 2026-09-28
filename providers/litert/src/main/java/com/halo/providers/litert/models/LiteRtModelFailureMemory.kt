package com.halo.providers.litert.models

import com.halo.sdk.provider.LocalComputeBackend
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per model + artifact fingerprint + backend health memory.
 *
 * "LiteRT failed" is not a diagnosis. A GPU failure for Qwen 3 1.7B must not poison Qwen 3 0.6B,
 * and a failure recorded against one artifact fingerprint must never follow a newly downloaded
 * revision. Records are therefore keyed by `modelId + sha256`.
 */
data class LiteRtModelFailureRecord(
    val modelId: String,
    /** Artifact SHA-256, or null when the artifact has no computable fingerprint yet. */
    val artifactFingerprint: String?,
    val lastLoadFailure: String? = null,
    val lastLoadFailureStage: String? = null,
    val lastLoadFailureBackend: LocalComputeBackend? = null,
    val lastGenerationFailure: String? = null,
    val lastGenerationFailureStage: String? = null,
    val lastGenerationFailureBackend: LocalComputeBackend? = null,
    val lastSuccessfulGenerationMs: Long? = null,
    val lastSuccessfulBackend: LocalComputeBackend? = null,
    val lastSuccessfulContext: Int? = null,
    val generationProven: Boolean = false,
    /** backend name -> last failure stage observed for that backend. */
    val loadFailuresByBackend: Map<String, String> = emptyMap(),
    val generationFailuresByBackend: Map<String, String> = emptyMap(),
    val generationSuccessBackends: Set<String> = emptySet(),
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    fun toJson(): String = JSONObject().apply {
        put("modelId", modelId)
        put("artifactFingerprint", artifactFingerprint)
        put("lastLoadFailure", lastLoadFailure)
        put("lastLoadFailureStage", lastLoadFailureStage)
        put("lastLoadFailureBackend", lastLoadFailureBackend?.name)
        put("lastGenerationFailure", lastGenerationFailure)
        put("lastGenerationFailureStage", lastGenerationFailureStage)
        put("lastGenerationFailureBackend", lastGenerationFailureBackend?.name)
        put("lastSuccessfulGenerationMs", lastSuccessfulGenerationMs ?: JSONObject.NULL)
        put("lastSuccessfulBackend", lastSuccessfulBackend?.name)
        put("lastSuccessfulContext", lastSuccessfulContext ?: JSONObject.NULL)
        put("generationProven", generationProven)
        put("loadFailuresByBackend", JSONObject(loadFailuresByBackend.toMap()))
        put("generationFailuresByBackend", JSONObject(generationFailuresByBackend.toMap()))
        put("generationSuccessBackends", JSONArray(generationSuccessBackends.toList()))
        put("updatedAtMs", updatedAtMs)
    }.toString()

    /** One bounded, UI-safe line for the Model Browser. */
    fun summaryLine(): String = buildString {
        append("fingerprint=${artifactFingerprint?.take(12) ?: "unknown"}")
        lastLoadFailureStage?.let { append(" · lastLoadFailure=$it") }
        lastGenerationFailureStage?.let { append(" · lastGenerationFailure=$it") }
        lastSuccessfulBackend?.let { append(" · provenBackend=$it") }
        if (generationProven) append(" · generationProven")
    }

    companion object {
        fun fromJson(json: String, fallbackModelId: String): LiteRtModelFailureRecord? = runCatching {
            val obj = JSONObject(json)
            LiteRtModelFailureRecord(
                modelId = obj.optString("modelId").ifBlank { fallbackModelId },
                artifactFingerprint = obj.optString("artifactFingerprint").ifBlank { null },
                lastLoadFailure = obj.optString("lastLoadFailure").ifBlank { null },
                lastLoadFailureStage = obj.optString("lastLoadFailureStage").ifBlank { null },
                lastLoadFailureBackend = backend(obj.optString("lastLoadFailureBackend").ifBlank { null }),
                lastGenerationFailure = obj.optString("lastGenerationFailure").ifBlank { null },
                lastGenerationFailureStage = obj.optString("lastGenerationFailureStage").ifBlank { null },
                lastGenerationFailureBackend = backend(obj.optString("lastGenerationFailureBackend").ifBlank { null }),
                lastSuccessfulGenerationMs = obj.optLong("lastSuccessfulGenerationMs", Long.MIN_VALUE)
                    .takeIf { it != Long.MIN_VALUE && it > 0L },
                lastSuccessfulBackend = backend(obj.optString("lastSuccessfulBackend").ifBlank { null }),
                lastSuccessfulContext = obj.optInt("lastSuccessfulContext", -1).takeIf { it > 0 },
                generationProven = obj.optBoolean("generationProven", false),
                loadFailuresByBackend = obj.optJSONObject("loadFailuresByBackend")?.toStringMap().orEmpty(),
                generationFailuresByBackend = obj.optJSONObject("generationFailuresByBackend")?.toStringMap().orEmpty(),
                generationSuccessBackends = obj.optJSONArray("generationSuccessBackends")?.toStringSet().orEmpty(),
                updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis()),
            )
        }.getOrNull()

        private fun backend(name: String?): LocalComputeBackend? =
            name?.let { stored -> LocalComputeBackend.entries.firstOrNull { it.name == stored } }

        private fun JSONObject.toStringMap(): Map<String, String> =
            keys().asSequence().associateWith { key -> optString(key) }

        private fun JSONArray.toStringSet(): Set<String> =
            (0 until length()).mapNotNull { optString(it).ifBlank { null } }.toSet()
    }
}

class LiteRtModelFailureMemory(private val storage: Storage) {

    private val _records = MutableStateFlow<Map<String, LiteRtModelFailureRecord>>(emptyMap())

    /** Observable records for the development UI, keyed by model id. */
    val records: StateFlow<Map<String, LiteRtModelFailureRecord>> = _records.asStateFlow()

    fun record(modelId: String, artifactFingerprint: String?): LiteRtModelFailureRecord {
        val key = storageKey(modelId, artifactFingerprint)
        _records.value[modelId]?.let { cached ->
            if (cached.artifactFingerprint == artifactFingerprint) return cached
        }
        val loaded = storage.getString(key)
            ?.let { LiteRtModelFailureRecord.fromJson(it, modelId) }
            ?: LiteRtModelFailureRecord(modelId = modelId, artifactFingerprint = artifactFingerprint)
        publish(modelId, loaded)
        return loaded
    }

    fun isGenerationProven(modelId: String, artifactFingerprint: String?): Boolean =
        record(modelId, artifactFingerprint).generationProven

    fun recordLoadFailure(
        modelId: String,
        artifactFingerprint: String?,
        stage: String,
        backend: LocalComputeBackend?,
        message: String?,
    ) {
        val current = record(modelId, artifactFingerprint)
        val backendKey = backend?.name
        publish(
            modelId,
            current.copy(
                lastLoadFailure = message?.take(300),
                lastLoadFailureStage = stage,
                lastLoadFailureBackend = backend,
                loadFailuresByBackend = if (backendKey == null) current.loadFailuresByBackend
                else current.loadFailuresByBackend + (backendKey to stage),
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    fun recordGenerationFailure(
        modelId: String,
        artifactFingerprint: String?,
        stage: String,
        backend: LocalComputeBackend?,
        message: String?,
    ) {
        val current = record(modelId, artifactFingerprint)
        val backendKey = backend?.name
        publish(
            modelId,
            current.copy(
                lastGenerationFailure = message?.take(300),
                lastGenerationFailureStage = stage,
                lastGenerationFailureBackend = backend,
                generationFailuresByBackend = if (backendKey == null) current.generationFailuresByBackend
                else current.generationFailuresByBackend + (backendKey to stage),
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    fun recordSuccess(
        modelId: String,
        artifactFingerprint: String?,
        backend: LocalComputeBackend?,
        contextTokens: Int?,
    ) {
        val current = record(modelId, artifactFingerprint)
        publish(
            modelId,
            current.copy(
                lastSuccessfulGenerationMs = System.currentTimeMillis(),
                lastSuccessfulBackend = backend,
                lastSuccessfulContext = contextTokens,
                generationProven = true,
                generationSuccessBackends = backend?.let { current.generationSuccessBackends + it.name }
                    ?: current.generationSuccessBackends,
                updatedAtMs = System.currentTimeMillis(),
            ),
        )
    }

    /** Clears the record for one fingerprint (used when a pin changes or an artifact is deleted). */
    fun forget(modelId: String, artifactFingerprint: String?) {
        storage.remove(storageKey(modelId, artifactFingerprint))
        _records.value = _records.value - modelId
    }

    private fun publish(modelId: String, record: LiteRtModelFailureRecord) {
        _records.value = _records.value + (modelId to record)
        storage.putString(storageKey(modelId, record.artifactFingerprint), record.toJson())
    }

    private fun storageKey(modelId: String, artifactFingerprint: String?): String =
        "litert_model_memory_${modelId}_${artifactFingerprint?.take(16) ?: "unknown"}"
}
