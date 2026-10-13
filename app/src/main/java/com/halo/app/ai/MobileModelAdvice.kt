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

    fun forModel(id: String, totalRamBytes: Long, deviceModel: String): MobileModelSuggestion? {
        if (!isEightGbClass(totalRamBytes)) return null
        val testedHere = deviceModel.equals(REDMI_TEST_DEVICE, ignoreCase = true)
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
