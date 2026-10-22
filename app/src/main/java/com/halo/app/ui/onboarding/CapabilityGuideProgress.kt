package com.halo.app.ui.onboarding

/** Setup receipts only. Android remains the sole authority for access grants. */
data class CapabilityGuideProgress(
    val active: Boolean = false,
    val currentId: String? = null,
    val attemptedIds: Set<String> = emptySet(),
    val skippedIds: Set<String> = emptySet(),
    val pendingId: String? = null,
) {
    internal fun encode(): String = listOf("1", active.toString(), currentId.orEmpty(),
        attemptedIds.sorted().joinToString(","), skippedIds.sorted().joinToString(","),
        pendingId.orEmpty()).joinToString("|")

    companion object {
        val ids = setOf("microphone", "notifications", "contacts", "calendar_read", "calendar_write",
            "camera", "location", "bluetooth", "nearby_wifi", "local_network", "notification_access",
            "accessibility", "write_settings", "notification_policy", "overlay", "assistant", "exact_alarm")

        internal fun decode(raw: String?): CapabilityGuideProgress {
            if (raw == null || raw.length > 2048) return CapabilityGuideProgress()
            val parts = raw.split('|')
            if (parts.size != 6 || parts[0] != "1" || parts[1] !in setOf("true", "false")) return CapabilityGuideProgress()
            fun id(value: String) = value.takeIf { it in ids }
            fun idSet(value: String) = value.split(',').filter { it in ids }.toSet()
            val current = id(parts[2])
            return CapabilityGuideProgress(parts[1] == "true" && current != null, current,
                idSet(parts[3]), idSet(parts[4]), id(parts[5])?.takeIf { it == current })
        }
    }
}
