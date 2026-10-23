package com.halo.app.ui.screens.home

import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes the existing host commands; tickets invalidate work still waiting for its turn. */
internal class VoiceInputCommandQueue {
    private val mutex = Mutex()
    private val revision = AtomicLong()
    fun current(): Long = revision.get()
    fun supersede(): Long = revision.incrementAndGet()
    fun isCurrent(ticket: Long): Boolean = ticket == revision.get()

    suspend fun <T> withLock(action: suspend () -> T): T = mutex.withLock { action() }

    suspend fun runIfCurrent(ticket: Long, allowed: () -> Boolean = { true }, onDenied: () -> Unit = {}, action: suspend () -> Unit): Boolean =
        mutex.withLock {
            if (!isCurrent(ticket)) return@withLock false
            if (!allowed()) {
                if (isCurrent(ticket)) onDenied()
                return@withLock false
            }
            action()
            true
        }
}
