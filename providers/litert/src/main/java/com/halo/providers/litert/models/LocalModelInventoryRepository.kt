package com.halo.providers.litert.models

import kotlinx.coroutines.flow.StateFlow

enum class ModelInventoryRefreshReason { STARTUP, USER_REQUEST, DOWNLOAD_COMPLETED, INVALIDATED }

/**
 * How much Halo actually knows about an on-disk artifact.
 *
 * `valid == true` is no longer implied by "the file is non-empty": a catalog artifact is only
 * verified once its physical bytes and SHA-256 match the pinned catalog entry.
 */
enum class LocalModelValidation {
    /** Bytes and SHA-256 match a pinned catalog artifact. Safe to select and load. */
    VERIFIED_ARTIFACT,

    /** Arbitrary user import. Physically present but its provenance is unknown. */
    UNVERIFIED,

    /** Known artifact that no longer matches its pinned fingerprint, or a damaged file. */
    CORRUPT,

    /** Structurally unusable (missing artifact for a known catalog entry, wrong container). */
    INCOMPATIBLE,
}

data class LocalModelDescriptor(
    val id: String,
    val displayName: String,
    val path: String,
    val bytes: Long,
    val modifiedAt: Long,
    val sha256: String?,
    val contextWindowTokens: Int,
    val supportsVision: Boolean,
    val supportsTools: Boolean,
    val validation: LocalModelValidation,
    val supportsText: Boolean = true,
    val supportsAudio: Boolean = false,
    val supportsThinking: Boolean = false,
    /** Model family as declared by the catalog, or null for arbitrary imports. */
    val family: String? = null,
    val quantizationLabel: String? = null,
    /** How this artifact may be obtained. Null for artifacts Halo does not catalogue. */
    val catalogAccess: LiteRtCatalogAccess? = null,
    val maturity: LiteRtModelMaturity? = null,
    val minDeviceRamGb: Int? = null,
    /**
     * True when the artifact's context window is unknown (arbitrary imports). The runtime then
     * asks LiteRT-LM for the model default instead of forcing a guessed 4096 tokens, which can
     * make an otherwise valid model fail engine initialization.
     */
    val useModelDefaultContext: Boolean = false,
    /** Bounded, user-safe explanation for a non-verified state. */
    val validationDetail: String? = null,
) {
    /** Usable for loading: verified, or physically intact but unverified user import. */
    val valid: Boolean
        get() = validation == LocalModelValidation.VERIFIED_ARTIFACT || validation == LocalModelValidation.UNVERIFIED

    /** Proven against a pinned catalog fingerprint. Only this state may advertise itself as ready. */
    val verified: Boolean
        get() = validation == LocalModelValidation.VERIFIED_ARTIFACT

    /** True when the artifact can only be acquired by importing a licensed file from device. */
    val importOnly: Boolean
        get() = catalogAccess == LiteRtCatalogAccess.GATED_IMPORT_ONLY ||
            catalogAccess == LiteRtCatalogAccess.MANUAL_IMPORT_ONLY
}

sealed interface LocalModelInventoryState {
    data object Loading : LocalModelInventoryState
    data class Ready(val models: List<LocalModelDescriptor>, val scannedAt: Long, val revision: Long = scannedAt) : LocalModelInventoryState
    data class Failed(val message: String) : LocalModelInventoryState
}

interface LocalModelInventoryRepository {
    val inventoryState: StateFlow<LocalModelInventoryState>
    suspend fun refresh(reason: ModelInventoryRefreshReason)
    suspend fun resolveModel(modelId: String): LocalModelDescriptor?
    /** Explicit verification must bypass cached physical fingerprints. Never called per turn. */
    suspend fun verifyPhysicalArtifact(modelId: String): LocalModelDescriptor? = resolveModel(modelId)
    /** Pins a valid physical import by measured hash; does not confer catalog provenance. */
    suspend fun acquirePhysicalArtifactLease(modelId: String, expectedSha256: String): Pair<LocalModelDescriptor, java.io.Closeable>? = null
    /** Exact physical generation pinned against replacement/deletion until the native owner closes it. */
    suspend fun acquireVerifiedArtifactLease(modelId: String, expectedSha256: String): Pair<LocalModelDescriptor, java.io.Closeable>? = null
    fun invalidate(reason: String)
}
