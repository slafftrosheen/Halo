package com.halo.runtime.cognition.context

/** Compatible benchmark calibration. Throughput is estimated, never tokenizer accurate. */
data class LocalInferenceRuntimeProfile(
    val providerId: String,
    val modelId: String,
    val backend: String,
    val contextTokens: Int?,
    val loadMs: Long?,
    val ttftMs: Long?,
    val estimatedGenerationTokensPerSecond: Double?,
    val peakEstimatedMemoryBytes: Long?,
    val stable: Boolean,
    val measuredAtMs: Long,
)

fun interface LocalInferenceProfileSource {
    suspend fun profile(providerId: String, modelId: String): LocalInferenceRuntimeProfile?
}

data class LocalInferenceObservation(
    val providerId: String,
    val modelId: String,
    val backend: String? = null,
    val ttftMs: Long? = null,
    val totalMs: Long? = null,
    val estimatedInputTokens: Int? = null,
    val estimatedOutputTokens: Int? = null,
    val success: Boolean,
    val coldStart: Boolean? = null,
    val outcome: LocalInferenceObservationOutcome = if (success) LocalInferenceObservationOutcome.SUCCESS else LocalInferenceObservationOutcome.GENERATION_FAILURE,
    val executionIdentity: String? = null,
)

/** Observations are content free and do not replace benchmark calibration. */
fun interface LocalInferenceObservationSink {
    suspend fun record(observation: LocalInferenceObservation)
}

enum class LocalInferenceObservationOutcome {
    SUCCESS, GENERATION_FAILURE, LOAD_FAILURE, TIMEOUT, RESOURCE_BUSY, CANCELLED, PREEMPTED,
}
