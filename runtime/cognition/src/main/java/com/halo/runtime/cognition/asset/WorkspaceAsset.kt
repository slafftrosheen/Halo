package com.halo.runtime.cognition.asset

import com.halo.runtime.cognition.provenance.ProvenanceRecord

enum class WorkspaceAssetType {
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    OTHER,
}

sealed interface AssetStorage {
    data class Owned(val relativePath: String) : AssetStorage
    data class ExternalUri(val uriString: String, val permissionPersisted: Boolean = false) : AssetStorage
}

data class WorkspaceAsset(
    val id: String,
    val objectId: String,
    val type: WorkspaceAssetType,
    val mimeType: String,
    val storage: AssetStorage,
    val displayName: String?,
    val bytes: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val provenance: ProvenanceRecord? = null,
)

enum class DerivedTextKind {
    TRANSCRIPT,
    OCR,
    CAPTION,
    SUMMARY,
}

data class DerivedAssetText(
    val assetId: String,
    val kind: DerivedTextKind,
    val text: String,
    val generator: String,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)
