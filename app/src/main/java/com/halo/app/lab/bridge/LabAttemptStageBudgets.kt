package com.halo.app.lab.bridge

/** Persisted stage caps for the isolated worker; each timeout ends in confirmed worker death. */
data class LabAttemptStageBudgets(val loadMs: Long, val executeMs: Long, val unloadMs: Long) {
    val totalMs: Long? = runCatching { Math.addExact(Math.addExact(loadMs, executeMs), unloadMs) }.getOrNull()

    fun valid(): Boolean = listOf(loadMs, executeMs, unloadMs).all { it in MIN_STAGE_MS..MAX_STAGE_MS } &&
        totalMs?.let { it <= MAX_TOTAL_MS } == true

    fun toJson(): org.json.JSONObject = org.json.JSONObject()
        .put("loadMs", loadMs).put("executeMs", executeMs).put("unloadMs", unloadMs)

    companion object {
        const val MIN_STAGE_MS = 1_000L
        const val MAX_STAGE_MS = 1_800_000L
        const val MAX_TOTAL_MS = 90L * 60L * 1_000L
        val LEGACY = LabAttemptStageBudgets(20_000L, 30_000L, 10_000L)

        fun parse(json: org.json.JSONObject?): LabAttemptStageBudgets? {
            if (json == null) return null
            val value = runCatching {
                LabAttemptStageBudgets(json.getLong("loadMs"), json.getLong("executeMs"), json.getLong("unloadMs"))
            }.getOrNull() ?: return null
            return value.takeIf { it.valid() }
        }
    }
}
