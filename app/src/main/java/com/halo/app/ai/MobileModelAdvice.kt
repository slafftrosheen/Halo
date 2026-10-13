package com.halo.app.ai

/**
 * Read-only, source-backed guidance for the Android 8 GB development lane.
 *
 * Deliberately NOT model selection, role qualification, a backend override or a claim about
 * arbitrary phones. Measured findings apply only to the named Redmi tester and pinned artifacts;
 * any replacement model/build needs its own acceptance result.
 */
data class MobileModelSuggestion(
    val role: String,
    val explanation: String,
    val needsQualification: Boolean = true,
)

object MobileModelAdvice {
    private const val GIB = 1024L * 1024L * 1024L
    private const val SMALL_PHONE_LIMIT = 9L * GIB
    private const val REDMI_TEST_DEVICE = "23124RA7EO"

    fun isEightGbClass(totalRamBytes: Long): Boolean =
        totalRamBytes > 0 && totalRamBytes <= SMALL_PHONE_LIMIT

    fun tierLabel(totalRamBytes: Long): String =
        com.halo.sdk.runtime.MobileNativeComputePolicy.tier(totalRamBytes).label

    fun computePlan(totalRamBytes: Long): String =
        when (com.halo.sdk.runtime.MobileNativeComputePolicy.tier(totalRamBytes)) {
            com.halo.sdk.runtime.MobileNativeComputePolicy.DeviceTier.FOUR_GB ->
                "CPU-first Qwen 0.6B or smaller; Whisper Tiny / Android STT; Android TTS. Avoid simultaneous heavy model residency."
            com.halo.sdk.runtime.MobileNativeComputePolicy.DeviceTier.SIX_GB ->
                "CPU-first Qwen 0.6B; compare SmolLM2 360M and Whisper Base/Tiny. GPU is manual testing only."
            com.halo.sdk.runtime.MobileNativeComputePolicy.DeviceTier.EIGHT_GB ->
                "Separate CPU speech and qualified CPU/GPU LLM. Compare warm latency and full voice loops."
            else -> "Device-specific runtime and model qualification required."
        }

    private val testedRedmiArtifacts = mapOf(
        "qwen3-0.6b-dynamic-int4" to "03e7da1eb1108b50dffaa9bb52cc7bcbad2eb0c66ca990267f480c1e545d2856",
        "smollm2-135m-instruct" to "ccdc5c85735743f081b7d44ca309cab569f76c0f2f0e8e163449a63721969c37",
        "qwen3-1.7b-dynamic-int4" to "2eeffef7b51bc3e1225ea69fe7aa5f417397934b56a5b6c20cc068d6fd2c918b",
        "deepseek-r1-distill-qwen-1.5b-q8" to "69b35f01759eed765641ab4af589bbe98131fd2825662a086d9037409b8c1295",
    )

    fun forModel(id: String, totalRamBytes: Long, deviceModel: String, artifactSha256: String? = null): MobileModelSuggestion? {
        if (!isEightGbClass(totalRamBytes)) return null
        val testedHere = deviceModel.equals(REDMI_TEST_DEVICE, ignoreCase = true) &&
            artifactSha256 != null && artifactSha256.equals(testedRedmiArtifacts[id], ignoreCase = true)
        return when (id) {
            "qwen3-0.6b-dynamic-int4" -> MobileModelSuggestion(
                "First interactive candidate",
                if (testedHere) "Redmi evidence: GPU 3/3 stability passes, 4.1 s first token after ~9.7 s load. Keep warm; test real turns and latency."
                else "Small Qwen baseline. Use CPU until this device/build/artifact passes the GPU stability probe.",
            )
            "smollm2-135m-instruct" -> MobileModelSuggestion(
                "Latency control, not assistant default",
                if (testedHere) "Redmi CPU loads quickly, but the model failed to reliably follow a simple output instruction. Do not infer response quality from speed."
                else "Small CPU control model. Require instruction-following and role qualification before assigning user tasks.",
            )
            "smollm2-360m-instruct" -> MobileModelSuggestion(
                "CPU fast-path experiment",
                "Use the existing role suite to compare responsiveness and strict instruction following against Qwen 0.6B; no phone acceptance yet.",
            )
            "qwen3-1.7b-dynamic-int4" -> MobileModelSuggestion(
                "Larger chat candidate: CPU first",
                if (testedHere) "Redmi CPU generates; GPU fails before first token with an OpenCL work-group error. Do not use explicit GPU for normal conversations."
                else "GPU compatibility is artifact/device-specific. Require a stability probe; compare CPU response quality and warm latency.",
            )
            "deepseek-r1-distill-qwen-1.5b-q8" -> MobileModelSuggestion(
                "Reasoning-only experiment",
                if (testedHere) "Redmi CPU generates but GPU fails at native first token. Reasoning traces are not voice replies."
                else "Higher-cost reasoning model. Test CPU and clean final-answer extraction before any role assignment.",
            )
            "qwen2-0.5b-instruct-q8" -> MobileModelSuggestion(
                "Experimental CPU-first small Qwen",
                "Publisher reports fast runs on a flagship Snapdragon; Halo has no 4/6/8 GB physical result yet. Pinned 647 MB file, conservative 1K context. Compare against the smaller Qwen3 0.6B.",
            )
            "qwen2.5-1.5b-instruct-q8" -> MobileModelSuggestion(
                "Instruction-following challenger",
                "Potential CPU chat alternative to Qwen3; run its strict chat and role suites. No measured Redmi pass in the October 13 matrix.",
            )
            "gemma3-1b-it-int4" -> MobileModelSuggestion(
                "Licensed small-model challenger",
                "Import only after accepting the upstream Gemma license; compare against Qwen3 for short final responses. Never auto-download.",
            )
            else -> null
        }
    }
}
