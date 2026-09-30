package com.halo.providers.litert

/**
 * MINING-P0-001 how Halo can honestly describe a tool loop for one LiteRT artifact.
 *
 * A catalog `supportsTools` boolean is a DECLARATION. It is not device/runtime proof, so it may
 * make an artifact eligible for probing; it must never by itself mean `ToolCallProtocol.NATIVE`.
 *
 * Kept inside `providers:litert`: this is LiteRT implementation detail, not a provider-SDK
 * contract. The provider-facing enum stays `NONE / NATIVE / HALO_PROMPT` and is derived from this.
 */
enum class LiteRtToolQualification {
    /** The artifact cannot carry any tool loop. */
    NOT_APPLICABLE,

    /** Catalog metadata declares native tool support, but Halo has never observed it. */
    DECLARED,

    /** No native claim, but the artifact is text-capable and a probe could promote it. */
    PROBE_REQUIRED,

    /** A probe is executing right now. */
    PROBING,

    /** Halo observed a real native tool call for this exact artifact/runtime/schema key. */
    NATIVE_PROVEN,

    /** Native is unproven, but Halo's prompt-mediated envelope loop is available. */
    PROMPT_MEDIATED,

    /** The last explicit probe failed; proof is absent but the model is not broken. */
    FAILED_LAST_PROBE,
    ;

    /** Human wording for the diagnostic surfaces. Never claims more than was observed. */
    val label: String
        get() = when (this) {
            NOT_APPLICABLE -> "Not applicable"
            DECLARED -> "Declared — probe required"
            PROBE_REQUIRED -> "Probe required"
            PROBING -> "Probing"
            NATIVE_PROVEN -> "Native proven"
            PROMPT_MEDIATED -> "Prompt mediated"
            FAILED_LAST_PROBE -> "Last probe failed"
        }
}

/**
 * Native tool proof cannot be keyed by model id alone: the same bytes against another LiteRT-LM
 * version, or another tool-schema contract, may behave differently. Stale proof must not survive an
 * incompatible runtime/schema change.
 */
object LiteRtToolQualificationKey {
    const val SCHEMA_CONTRACT_VERSION = "halo-openapi-v1"

    fun of(artifactFingerprint: String, runtimeVersion: String): String =
        "$artifactFingerprint:litertlm-$runtimeVersion:$SCHEMA_CONTRACT_VERSION"
}

/**
 * Content-free tool-channel truth for one artifact. Never carries argument values, only counts,
 * digests and structural booleans.
 */
data class LiteRtToolChannelDiagnostics(
    val modelId: String,
    val qualification: LiteRtToolQualification,
    /** `<sha256>:litertlm-<version>:<schema>`; the unit native proof is actually stored against. */
    val qualificationKey: String?,
    val artifactFingerprintPrefix: String?,
    val runtimeVersion: String,
    val schemaContractVersion: String,
    val configuredToolCount: Int,
    val nativeToolCallObserved: Boolean,
    val callIdComplete: Boolean,
    val argumentsStructurallyComplete: Boolean,
    val pairedResponseCount: Int,
    val lastProbeAtMs: Long?,
    val lastProbeFailureStage: String?,
    val lastProbeFailureDetail: String?,
) {
    fun summaryLine(): String = buildString {
        append("tools=${qualification.label}")
        append(" key=${qualificationKey?.take(24) ?: "none"}")
        append(" configured=$configuredToolCount")
        append(" nativeObserved=$nativeToolCallObserved")
        lastProbeFailureStage?.let { append(" lastProbe=$it") }
    }
}
