package com.halo.core.runtime

/**
 * The canonical state machine of the HAL Runtime Kernel.
 */
enum class RuntimeState {
    BOOT,
    INITIALIZING,
    READY,
    LISTENING,
    PROCESSING,
    SLEEPING,
    SHUTDOWN,
    ERROR
}
