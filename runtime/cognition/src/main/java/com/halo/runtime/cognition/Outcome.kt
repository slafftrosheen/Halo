package com.halo.runtime.cognition

sealed interface Outcome {
    data class Success(val message: String? = null) : Outcome
    data class Failure(val reason: String, val error: Throwable? = null) : Outcome
    data class Partial(val message: String, val progress: Float = 0.5f) : Outcome
    data class Cancelled(val reason: String? = null) : Outcome
}
