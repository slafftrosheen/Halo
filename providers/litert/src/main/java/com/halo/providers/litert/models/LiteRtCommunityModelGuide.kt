package com.halo.providers.litert.models

/** Discovered Hub metadata remains source identity, NEVER successful native or role qualification. */
data class LiteRtCommunityModelNote(
    val description: String,
    val needsConversationIsolation: Boolean = false,
)

/**
 * Explicit family guidance for community variants, separated from the pinned built-in catalog.
 * Most variants must still be imported/downloaded by the user and exercised on their phone.
 * The INT1 Bonsai PoC is excluded from downloader suggestions because the publisher says
 * it cannot execute in released LiteRT (no INT1 kernel). Working ternary artifacts are NOT blocked.
 */
object LiteRtCommunityModelGuide {
    const val BONSAI_INT1_UNRUNNABLE_SHA256 =
        "cb23e2285ac9d388981eb94100368a10f2f6a707f50c9c6745a59229ada91e2b"

    fun knownNonRunnable(repo: String, filename: String): Boolean =
        repo.equals("litert-community/1Bit-Bonsai-1.7B-PoC", ignoreCase = true) &&
            filename.equals("bonsai-1.7b-int1-REAL.litertlm", ignoreCase = true)

    fun nonRunnableFingerprint(sha256: String?): Boolean =
        BONSAI_INT1_UNRUNNABLE_SHA256.equals(sha256, ignoreCase = true)

    fun guide(repo: String, filename: String): LiteRtCommunityModelNote? {
        val combined = "$repo/$filename".lowercase()
        return when {
            knownNonRunnable(repo, filename) -> LiteRtCommunityModelNote(
                "Cannot run: the INT1 Bonsai PoC has no LiteRT kernel. Use a ternary executable variant instead.")
            "ternary-bonsai" in combined -> LiteRtCommunityModelNote(
                "Ternary INT2: GPU preferred; supported builds may run on CPU. Confirm the exact kernel/backend and device.")
            "lfm2.5" in combined -> LiteRtCommunityModelNote(
                "Hybrid recurrent model; CPU first and isolated-engine multi-conversation validation required.",
                needsConversationIsolation = true)
            "granite-4.0-h" in combined || "qwen3.5" in combined -> LiteRtCommunityModelNote(
                "Recurrent-state model; do not assume separate conversations on one engine are isolated.",
                needsConversationIsolation = true)
            "granite" in combined -> LiteRtCommunityModelNote(
                "Granite model: check exact architecture, thinking mode and backend on-device.")
            else -> null
        }
    }
}
