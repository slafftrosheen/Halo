package com.halo.providers.litert.models

import org.json.JSONObject
import java.io.File

/**
 * Resume bookkeeping for a partially downloaded artifact.
 *
 * Stored as `<id>.litertlm.part.json` next to `<id>.litertlm.part`. A resume is only attempted when
 * *every* pinned identity field matches the catalog entry exactly; anything else means the partial
 * belongs to a different artifact revision and must be discarded rather than appended to.
 */
data class LiteRtPartialDownloadMetadata(
    val modelId: String,
    val repoId: String,
    val revision: String,
    val filename: String,
    val expectedBytes: Long,
    val expectedSha256: String,
    val downloadedBytes: Long,
    val etag: String? = null,
    val lastModified: String? = null,
    val updatedAtMs: Long = System.currentTimeMillis(),
) {
    /** True only when the partial provably belongs to this exact pinned artifact. */
    fun matches(model: LiteRtCatalogModel): Boolean =
        modelId == model.id &&
            repoId == model.repoId &&
            revision == model.revision &&
            filename == model.filename &&
            expectedBytes == model.expectedBytes &&
            expectedSha256.equals(model.sha256, ignoreCase = true)

    fun toJson(): String = JSONObject().apply {
        put("modelId", modelId)
        put("repoId", repoId)
        put("revision", revision)
        put("filename", filename)
        put("expectedBytes", expectedBytes)
        put("expectedSha256", expectedSha256)
        put("downloadedBytes", downloadedBytes)
        put("etag", etag)
        put("lastModified", lastModified)
        put("updatedAtMs", updatedAtMs)
    }.toString()

    companion object {
        const val SUFFIX = ".json"

        /** Sidecar path for a partial artifact file. */
        fun sidecarFor(partFile: File): File = File(partFile.parentFile, partFile.name + SUFFIX)

        fun fromJson(json: String): LiteRtPartialDownloadMetadata? = runCatching {
            val obj = JSONObject(json)
            LiteRtPartialDownloadMetadata(
                modelId = obj.getString("modelId"),
                repoId = obj.getString("repoId"),
                revision = obj.getString("revision"),
                filename = obj.getString("filename"),
                expectedBytes = obj.getLong("expectedBytes"),
                expectedSha256 = obj.getString("expectedSha256"),
                downloadedBytes = obj.optLong("downloadedBytes", 0L),
                etag = obj.optString("etag").ifBlank { null },
                lastModified = obj.optString("lastModified").ifBlank { null },
                updatedAtMs = obj.optLong("updatedAtMs", System.currentTimeMillis()),
            )
        }.getOrNull()

        fun fromFile(file: File): LiteRtPartialDownloadMetadata? =
            if (!file.exists()) null
            else runCatching { fromJson(file.readText()) }.getOrNull()

        /** Fresh metadata for a brand new transfer. */
        fun forArtifact(model: LiteRtCatalogModel, downloadedBytes: Long = 0L): LiteRtPartialDownloadMetadata =
            LiteRtPartialDownloadMetadata(
                modelId = model.id,
                repoId = model.repoId,
                revision = model.revision,
                filename = model.filename,
                expectedBytes = model.expectedBytes,
                expectedSha256 = model.sha256,
                downloadedBytes = downloadedBytes,
            )
    }
}
