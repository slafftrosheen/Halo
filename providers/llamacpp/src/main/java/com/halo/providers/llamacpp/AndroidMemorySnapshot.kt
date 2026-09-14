package com.halo.providers.llamacpp

import android.app.ActivityManager
import android.content.Context
import android.os.Debug

/** Small, conservative memory snapshot used by local-model admission. */
internal object AndroidMemorySnapshot {
    data class Snapshot(
        val totalBytes: Long,
        val availableBytes: Long,
        val lowMemory: Boolean,
        val appPssBytes: Long
    )

    fun read(context: Context): Snapshot {
        val info = ActivityManager.MemoryInfo()
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (manager != null) manager.getMemoryInfo(info)
        return Snapshot(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            lowMemory = info.lowMemory,
            appPssBytes = Debug.getPss() * 1024L
        )
    }
}
