package com.halo.runtime.cognition

enum class ActionFailureCode {
    CAPABILITY_NOT_FOUND,
    UNSUPPORTED_ACTION,

    APP_NOT_FOUND,
    APP_AMBIGUOUS,
    APP_VISIBILITY_UNAVAILABLE,
    APP_NOT_LAUNCHABLE,

    REMINDER_TIME_MISSING,
    REMINDER_TIME_INVALID,
    EXACT_ALARM_ACCESS_REQUIRED,

    PERMISSION_REQUIRED,
    PLATFORM_REJECTED,
    TIMEOUT,
    UNKNOWN,
}

sealed interface Outcome {
    data class Success(val message: String? = null) : Outcome
    data class Dispatched(val message: String, val target: String? = null) : Outcome
    data class PermanentFailure(
        val reason: String,
        val error: Throwable? = null,
        val code: ActionFailureCode? = null,
    ) : Outcome
    data class RetryableFailure(
        val reason: String,
        val error: Throwable? = null,
        val retryCount: Int = 0,
        val code: ActionFailureCode? = null,
    ) : Outcome
    data class Partial(
        val message: String,
        val progress: Float = 0.5f,
        val code: ActionFailureCode? = null,
    ) : Outcome
    data class Cancelled(val reason: String? = null) : Outcome
    data class NeedsClarification(val prompt: String, val field: String) : Outcome
}
