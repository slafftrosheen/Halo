package com.halo.app.ai

/** Conservative admission guidance, distinct from an artifact/runtime-qualified native test. */
object LocalModelDeviceFit {
    enum class Verdict(val label: String) {
        ESTIMATED_FIT("Estimated to fit this phone"),
        MEMORY_PRESSURE("May need more free memory"),
        STORAGE_SHORTAGE("Not enough free storage")
    }
    fun assess(bytes: Long, freeStorage: Long, freeMemory: Long, totalMemory: Long, ramFloorGb: Int?): Verdict {
        require(bytes > 0 && bytes <= 8L*1024*1024*1024)
        val requiredStorage=bytes + bytes/10 + 256L*1024*1024
        if(freeStorage>0 && requiredStorage>freeStorage) return Verdict.STORAGE_SHORTAGE
        if(bytes*2>freeMemory || (ramFloorGb != null && ramFloorGb.toLong()*1024*1024*1024>totalMemory))
            return Verdict.MEMORY_PRESSURE
        return Verdict.ESTIMATED_FIT
    }
}
