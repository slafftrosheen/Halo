package com.halo.providers.llamacpp

enum class ModelTransferState {
    REMOTE, QUEUED, DOWNLOADING, PAUSED, VERIFYING, DOWNLOADED, FAILED, CANCELLED
}

enum class ModelValidationState {
    UNKNOWN, VALIDATING, VALID, UNSUPPORTED_ARCHITECTURE, INVALID_GGUF, SIZE_MISMATCH
}
