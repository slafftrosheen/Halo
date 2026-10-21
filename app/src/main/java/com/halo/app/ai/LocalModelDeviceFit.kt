package com.halo.app.ai

/** Conservative admission guidance, distinct from an artifact/runtime-qualified native test. */
object LocalModelDeviceFit {
    enum class Verdict(val label: String) {
        ESTIMATED_FIT("Estimated to fit this phone"),
        MEMORY_PRESSURE("May need more free memory"),
        STORAGE_SHORTAGE("Not enough free storage"),
        UNKNOWN("Device fit is unknown"),
    }

    fun assess(bytes: Long, freeStorage: Long, freeMemory: Long, totalMemory: Long, ramFloorGb: Int?): Verdict {
        // External/stale catalogue metadata must not crash the settings screen or overflow.
        if (bytes <= 0 || bytes > 8L * GIB || (ramFloorGb != null && ramFloorGb <= 0)) return Verdict.UNKNOWN
        if (freeStorage < 0) return Verdict.UNKNOWN
        val requiredStorage = bytes + bytes / 10 + 256L * 1024 * 1024
        // Zero space is not an omitted/unlimited measurement.
        if (requiredStorage > freeStorage) return Verdict.STORAGE_SHORTAGE
        if (totalMemory <= 0 || freeMemory < 0) return Verdict.UNKNOWN
        if (ramFloorGb != null && ramFloorGb.toLong() * GIB > totalMemory) return Verdict.MEMORY_PRESSURE
        if (freeMemory > totalMemory) return Verdict.UNKNOWN
        if (bytes * 2 > freeMemory) return Verdict.MEMORY_PRESSURE
        return Verdict.ESTIMATED_FIT
    }

    private const val GIB = 1024L * 1024 * 1024
}
