package com.halo.providers.litert.models

import android.os.StatFs
import android.util.Log
import java.io.File

enum class LiteRtStorageVerdict {
    SUFFICIENT,

    /** Definitely not enough room: the download must not begin. */
    INSUFFICIENT,

    /** StatFs could not be read. Reported honestly instead of pretending there is room. */
    UNKNOWN,
}

data class LiteRtStoragePreflightResult(
    val requiredBytes: Long,
    val freeBytes: Long,
    val reserveBytes: Long,
    val finalizeOverheadBytes: Long,
    val verdict: LiteRtStorageVerdict,
) {
    /** UNKNOWN is treated as "proceed": a failed StatFs must not block installation forever. */
    val sufficient: Boolean
        get() = verdict != LiteRtStorageVerdict.INSUFFICIENT

    val shortfallBytes: Long
        get() = (requiredBytes - freeBytes).coerceAtLeast(0L)

    fun describe(): String = when (verdict) {
        LiteRtStorageVerdict.SUFFICIENT ->
            "Storage ok: ${formatGb(requiredBytes)} required, ${formatGb(freeBytes)} free"
        LiteRtStorageVerdict.INSUFFICIENT ->
            "Not enough free space: ${formatGb(requiredBytes)} required, ${formatGb(freeBytes)} free"
        LiteRtStorageVerdict.UNKNOWN ->
            "Storage check unavailable: ${formatGb(requiredBytes)} required"
    }

    companion object {
        private fun formatGb(bytes: Long): String = "%.2f GB".format(bytes / 1_000_000_000.0)
    }
}

/**
 * Free-storage preflight for large artifacts.
 *
 * A 1-2 GB download must fail *before* it starts rather than after the filesystem write fails
 * halfway through. Required room covers the artifact itself, a finalization/copy overhead and a
 * safety reserve for everything else the process still needs to write.
 */
object LiteRtStoragePreflight {

    private const val TAG = "HALO_LITERT_STORAGE"

    /** Keep this much headroom for the OS and other Halo writes. */
    const val DEFAULT_RESERVE_BYTES: Long = 512L * 1024L * 1024L

    /** Verified staging may briefly need extra room for the atomic-move fallback path. */
    const val DEFAULT_FINALIZE_OVERHEAD_FRACTION: Double = 0.05

    fun evaluate(
        expectedBytes: Long,
        freeBytes: Long,
        reserveBytes: Long = DEFAULT_RESERVE_BYTES,
        finalizeOverheadFraction: Double = DEFAULT_FINALIZE_OVERHEAD_FRACTION,
    ): LiteRtStoragePreflightResult {
        val overhead = (expectedBytes * finalizeOverheadFraction).toLong()
        val required = expectedBytes + overhead + reserveBytes
        val verdict = if (freeBytes < required) LiteRtStorageVerdict.INSUFFICIENT else LiteRtStorageVerdict.SUFFICIENT
        return LiteRtStoragePreflightResult(required, freeBytes, reserveBytes, overhead, verdict)
    }

    /**
     * Reads available bytes from [root] (its closest existing ancestor). Never throws: an
     * unreadable filesystem is reported as [LiteRtStorageVerdict.UNKNOWN].
     */
    fun check(
        root: File,
        expectedBytes: Long,
        reserveBytes: Long = DEFAULT_RESERVE_BYTES,
        finalizeOverheadFraction: Double = DEFAULT_FINALIZE_OVERHEAD_FRACTION,
    ): LiteRtStoragePreflightResult {
        val free = runCatching {
            val target = generateSequence(root) { it.parentFile }.firstOrNull { it.exists() } ?: root
            StatFs(target.absolutePath).availableBytes
        }.onFailure { error ->
            Log.w(TAG, "HALO_LITERT_STORAGE_UNKNOWN path=${root.absolutePath}", error)
        }.getOrNull()

        // A non-positive reading is not trustworthy (test doubles, restricted mounts): report
        // UNKNOWN and let the stream-level DISK_FULL classification catch a real full disk.
        if (free == null || free <= 0L) {
            val overhead = (expectedBytes * finalizeOverheadFraction).toLong()
            return LiteRtStoragePreflightResult(
                requiredBytes = expectedBytes + overhead + reserveBytes,
                freeBytes = -1L,
                reserveBytes = reserveBytes,
                finalizeOverheadBytes = overhead,
                verdict = LiteRtStorageVerdict.UNKNOWN,
            )
        }
        return evaluate(expectedBytes, free, reserveBytes, finalizeOverheadFraction)
    }
}
