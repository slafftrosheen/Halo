package com.halo.app

import android.os.Debug
import android.os.SystemClock
import android.util.Log

/** Lightweight startup timing trace for real-device diagnostics. */
object RuntimeStartupTrace {
    private const val TAG = "HaloStartup"

    fun mark(phase: String, startedAt: Long? = null) {
        val now = SystemClock.elapsedRealtime()
        val elapsed = startedAt?.let { now - it }
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val native = Debug.getNativeHeapAllocatedSize()
        Log.i(
            TAG,
            "phase=$phase elapsedMs=${elapsed ?: 0} thread=${Thread.currentThread().name} " +
                "heapUsedMb=${used / (1024L * 1024L)} nativeHeapMb=${native / (1024L * 1024L)}"
        )
    }

    inline fun <T> measure(phase: String, block: () -> T): T {
        val startedAt = SystemClock.elapsedRealtime()
        mark("$phase.begin", startedAt)
        return try {
            block()
        } finally {
            mark("$phase.end", startedAt)
        }
    }
}
