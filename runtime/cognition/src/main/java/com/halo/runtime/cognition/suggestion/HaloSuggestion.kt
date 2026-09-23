package com.halo.runtime.cognition.suggestion

import java.time.Instant

@JvmInline
value class SuggestionId(val value: String)

enum class SuggestionKind {
    UNFINISHED_CLARIFICATION,
    FAILED_ROUTINE,
    AGENT_RUN_AWAITING_APPROVAL,
    DEVICE_CAPABILITY_NEEDING_SETUP,
    MODEL_DOWNLOAD_READY,
    BRAIN_BACKUP_OVERDUE,
}

data class HaloSuggestion(
    val id: SuggestionId,
    val kind: SuggestionKind,
    val title: String,
    val summary: String? = null,
    val deduplicationKey: String,
    val expiresAt: Instant? = null,
)
