package com.halo.host.ink

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Stop blocking native input on cancellation, retaining ownership until its worker exits. */
internal suspend fun readPenUntilCancelled(stop: () -> Unit, read: () -> Unit): Unit = try {
    withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { runCatching(stop) }
            if (continuation.isActive) {
                try {
                    read()
                    continuation.resume(Unit)
                } catch (error: Throwable) {
                    continuation.resumeWithException(error)
                }
            }
        }
    }
} catch (cancelled: CancellationException) {
    // Cancellation can win the dispatcher handoff before the handler is installed.
    runCatching(stop)
    throw cancelled
}
