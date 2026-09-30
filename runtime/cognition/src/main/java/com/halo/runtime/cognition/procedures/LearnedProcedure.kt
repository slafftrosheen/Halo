package com.halo.runtime.cognition.procedures

/**
 * MINING-P1-001 learned procedures.
 *
 * A **Skill** is a capability/instruction package. A **LearnedProcedure** is a reusable recipe
 * composed from capabilities Halo already has. The distinction is deliberate and must not be
 * collapsed: procedures are instruction-only, they cannot add authority, and every step they mention
 * still runs through [com.halo.runtime.cognition.tools.ToolAvailabilityResolver], policy, approval,
 * the resource manager and the durable action journal at execution time.
 *
 * Harvested conceptually from Brownie's `SkillStore` learned-procedure metadata (usage counts,
 * staleness), and from Kotlin-ADK's SKILL.md interoperability idea. No trust model is copied.
 */
enum class ProcedureOrigin {
    USER_CREATED,
    AGENT_PROPOSED,
    IMPORTED,
}

data class ProcedureStep(
    /** An existing Halo tool/capability id. A procedure cannot invent a new one. */
    val capabilityId: String,
    /** Human-readable intent for this step. Instructions, never a smuggled command. */
    val intent: String,
)

data class LearnedProcedure(
    val id: String,
    val name: String,
    val description: String,
    val triggerPhrases: List<String>,
    val steps: List<ProcedureStep>,
    val origin: ProcedureOrigin,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long? = null,
    val usageCount: Long = 0,
    val enabled: Boolean = true,
) {
    /** Bounded staleness copy for the Skills UI. Never deletes anything. */
    fun usageLabel(nowMs: Long): String = when {
        usageCount <= 0L -> "Never used"
        lastUsedAt == null -> "Used $usageCount times"
        else -> {
            val days = ((nowMs - lastUsedAt) / 86_400_000L).coerceAtLeast(0L)
            when {
                days <= 0L -> "Used today"
                days == 1L -> "Last used yesterday"
                else -> "Last used $days days ago"
            }
        }
    }
}

/**
 * A proposal created by the model. Nothing is persisted until the user confirms it through Halo's
 * existing durable approval surface.
 */
data class ProcedureProposal(
    val proposalId: String,
    val name: String,
    val description: String,
    val triggerPhrases: List<String>,
    val steps: List<ProcedureStep>,
    /** Every step capability must exist at proposal time; unknown ids are rejected outright. */
    val unknownCapabilities: List<String>,
    val createdAt: Long,
) {
    val savable: Boolean get() = unknownCapabilities.isEmpty() && steps.isNotEmpty() && steps.size <= MAX_STEPS

    companion object {
        const val MAX_STEPS = 12
        const val MAX_NAME_CHARS = 80
        const val MAX_DESCRIPTION_CHARS = 400
        const val MAX_TRIGGERS = 8
        const val MAX_TRIGGER_CHARS = 80
    }
}

/** Durable storage abstraction. Implemented by the host; nothing here touches a database. */
interface LearnedProcedureStore {
    suspend fun all(): List<LearnedProcedure>
    suspend fun get(id: String): LearnedProcedure?
    suspend fun upsert(procedure: LearnedProcedure)
    suspend fun delete(id: String)
    suspend fun recordUsage(id: String, usedAtMs: Long)
}

/** In-memory store used by tests, previews and hosts that have no durable storage. */
class InMemoryLearnedProcedureStore(
    initial: List<LearnedProcedure> = emptyList(),
) : LearnedProcedureStore {
    private val procedures = LinkedHashMap<String, LearnedProcedure>()

    init {
        initial.forEach { procedures[it.id] = it }
    }

    override suspend fun all(): List<LearnedProcedure> = procedures.values.toList()

    override suspend fun get(id: String): LearnedProcedure? = procedures[id]

    override suspend fun upsert(procedure: LearnedProcedure) {
        procedures[procedure.id] = procedure
    }

    override suspend fun delete(id: String) {
        procedures.remove(id)
    }

    override suspend fun recordUsage(id: String, usedAtMs: Long) {
        val current = procedures[id] ?: return
        procedures[id] = current.copy(
            usageCount = current.usageCount + 1,
            lastUsedAt = usedAtMs,
            updatedAt = current.updatedAt,
        )
    }
}
