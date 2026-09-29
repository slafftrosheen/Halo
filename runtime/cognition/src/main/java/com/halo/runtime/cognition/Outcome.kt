package com.halo.runtime.cognition

sealed interface Outcome {
    data class Success(val message: String? = null) : Outcome
    data class Dispatched(val message: String, val target: String? = null) : Outcome
    data class PermanentFailure(val reason: String, val error: Throwable? = null) : Outcome
    data class RetryableFailure(
        val reason: String,
        val error: Throwable? = null,
        val retryCount: Int = 0
    ) : Outcome
    data class Partial(val message: String, val progress: Float = 0.5f) : Outcome
    data class Cancelled(val reason: String? = null) : Outcome
    data class NeedsClarification(val prompt: String, val field: String) : Outcome
}
