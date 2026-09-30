package com.halo.runtime.cognition.memory

import java.time.Instant
import java.security.MessageDigest

enum class MemoryKind { PREFERENCE, FACT, PERSON, PLACE, DEVICE, PROJECT, WORKFLOW, INSTRUCTION, EPISODE, RELATIONSHIP, PROCEDURE_REFERENCE }
enum class MemoryScope { GLOBAL, PROJECT, DEVICE, CONVERSATION, WORKSPACE_OBJECT }
enum class MemoryProposalOrigin { USER_EXPLICIT, USER_CORRECTION, MODEL_INFERRED, EPISODE_DERIVED, WORKSPACE_DERIVED, IMPORT }
enum class MemoryEvidenceKind { CONVERSATION_TURN, AGENT_RUN, WORKSPACE_OBJECT, USER_ACTION, ROUTINE_RUN, IMPORTED, EPISODE }
data class MemoryEvidenceRef(val kind: MemoryEvidenceKind, val id: String, val revision: String? = null) {
    init { require(id.length in 1..200 && (revision?.length ?: 0) <= 80) }
}
object MemoryLimits { const val CONTENT = 1000; const val PROPOSAL = 600; const val TITLE = 80; const val EVIDENCE = 12; const val PROPOSALS = 3; const val EPISODE = 1200 }
enum class MemorySensitivity { ORDINARY, SENSITIVE, SECRET, UNKNOWN }
/** Category metadata is required for inferred output; lexical secret checks are an additional guard. */
object MemorySensitivityClassifier {
    private val secrets = Regex("(?i)(password|passwd|api[ _-]?key|oauth[ _-]?token|access[ _-]?token|security[ _-]?code|credit[ _-]?card|bearer\\s+|-----BEGIN .*PRIVATE KEY|sk-[A-Za-z0-9]{12,})")
    private val sensitive = Regex("(?i)(health|medical|diagnos|religio|politic|sexual|criminal|financial secret|bank account)")
    fun classify(content: String, declared: MemorySensitivity = MemorySensitivity.UNKNOWN): MemorySensitivity = when {
        secrets.containsMatchIn(content) || Regex("\\b(?:[0-9][ -]?){13,19}\\b").containsMatchIn(content) -> MemorySensitivity.SECRET
        declared == MemorySensitivity.SECRET -> MemorySensitivity.SECRET
        sensitive.containsMatchIn(content) || declared == MemorySensitivity.SENSITIVE -> MemorySensitivity.SENSITIVE
        else -> declared
    }
    fun requireWritable(content: String) { require(content.trim().length in 1..MemoryLimits.CONTENT); require(classify(content) != MemorySensitivity.SECRET) { "Use secure credential configuration; secrets cannot be personal memory" } }
    fun mayPropose(content: String, origin: MemoryProposalOrigin, declared: MemorySensitivity): Boolean {
        val category = classify(content, declared)
        return category != MemorySensitivity.SECRET && (origin in setOf(MemoryProposalOrigin.USER_EXPLICIT, MemoryProposalOrigin.USER_CORRECTION) || category == MemorySensitivity.ORDINARY)
    }
}
@JvmInline value class EpisodeId(val value: String)
enum class EpisodeOutcome { SUCCESS, PARTIAL, FAILED, CANCELLED, BLOCKED, INTERRUPTED }
data class Episode(val id: EpisodeId, val interactionId: String?, val runId: String?, val startedAt: Instant,
    val endedAt: Instant, val outcome: EpisodeOutcome, val summary: String,
    val referencedObjectIds: Set<String> = emptySet(), val referencedMemoryIds: Set<String> = emptySet(),
    val toolIds: List<String> = emptyList(), val workflow: WorkflowSignature? = null,
    val unknownCompletion: Boolean = false, val scopeId: String? = null) {
    init {
        require(summary.length <= MemoryLimits.EPISODE && toolIds.size <= 12 && referencedObjectIds.size <= 20 && referencedMemoryIds.size <= 12)
        require(endedAt >= startedAt)
        require(listOfNotNull(id.value,interactionId,runId,scopeId).all { it.length in 1..200 })
        require((toolIds + referencedObjectIds + referencedMemoryIds).all { it.length in 1..200 })
    }
}
@JvmInline value class MemoryProposalId(val value: String)
data class MemoryProposal(val id: MemoryProposalId, val content: String, val kind: MemoryKind, val scope: MemoryScope,
    val scopeId: String?, val confidence: Float, val origin: MemoryProposalOrigin,
    val evidence: List<MemoryEvidenceRef>, val conflictMemoryIds: List<String> = emptyList(),
    val createdAt: Instant, val sensitivity: MemorySensitivity = MemorySensitivity.UNKNOWN, val subject: String = "") {
    init { require(content.trim().length in 1..MemoryLimits.PROPOSAL); require(confidence.isFinite() && confidence in 0f..1f); require(evidence.size in 1..MemoryLimits.EVIDENCE); require(conflictMemoryIds.size <= 5); require(subject.length <= 120); require(scope == MemoryScope.GLOBAL || !scopeId.isNullOrBlank()) }
}
enum class MemoryProposalState { PENDING, ACCEPTED, DISMISSED, EXPIRED, SUPERSEDED }
enum class MemoryConflictKind { NEW, DUPLICATE, REINFORCES, CONFLICTS, UPDATES }
object MemoryProposalFingerprint {
    fun normalize(content: String) = content.lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\b(the|a|an)\\b"), "").replace(Regex("\\s+"), " ").trim()
    fun subject(content: String): String = if(Regex("(?i)(celsius|fahrenheit)").containsMatchIn(content)) "temperature unit" else normalize(content)
    fun of(kind: MemoryKind, scope: MemoryScope, scopeId: String?, content: String, subjectText: String = "") = hash("$kind|$scope|${scopeId.orEmpty()}|${normalize(subjectText.ifBlank { subject(content) })}")
    fun hash(raw: String) = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
/** Only safe tool identities/operation classes, never values or hashes of private arguments. */
data class WorkflowSignature(val value: String, val tools: List<String>) {
    companion object { fun of(tools: List<String>): WorkflowSignature? = tools.take(12).takeIf { it.size >= 2 }?.let { WorkflowSignature(MemoryProposalFingerprint.hash(it.joinToString("|")), it) } }
}
enum class LearningPreference { OFF, EXPLICIT_ONLY, SUGGEST;
    companion object { fun fromStored(raw: String?) = entries.firstOrNull { it.name == raw } ?: EXPLICIT_ONLY }
}
data class LearningPreferences(val mode: LearningPreference = LearningPreference.EXPLICIT_ONLY, val suggestMemories: Boolean = true,
    val suggestProcedures: Boolean = true, val suggestAutomations: Boolean = true, val modelReflection: Boolean = false)
enum class ReflectionMode { NONE, DETERMINISTIC, MODEL }
enum class ReflectionStatus { NOT_REQUIRED, PENDING, COMPLETED, FAILED, SKIPPED }
enum class ReflectionUsefulness { NONE, OPERATIONAL, PERSONAL }
data class ProcedureSuggestion(val signature: WorkflowSignature, val evidenceCount: Int)
data class AutomationSuggestion(val routineId: String, val evidenceCount: Int)
data class Reflection(val episodeId: EpisodeId, val usefulness: ReflectionUsefulness, val memoryProposals: List<MemoryProposal> = emptyList(),
    val procedureSuggestion: ProcedureSuggestion? = null, val automationSuggestion: AutomationSuggestion? = null,
    val createdAt: Instant, val stage: com.halo.sdk.runtime.RuntimeStage = com.halo.sdk.runtime.RuntimeStage.REFLECTION)
data class ReflectionRequest(val episode: Episode, val explicitStatements: List<String>, val evidence: List<MemoryEvidenceRef>, val conflictingMemories: List<Pair<String,String>> = emptyList()) {
    init { require(explicitStatements.size <= 3 && explicitStatements.all { it.length <= 1000 }); require(evidence.size <= 12 && conflictingMemories.size <= 5) }
}
data class ReflectionDraft(val memoryCandidates: List<MemoryProposal>)
fun interface ReflectionModel { suspend fun reflect(request: ReflectionRequest): ReflectionDraft? }
object ReflectionPolicy {
    const val VERSION = 1
    fun mode(episode: Episode?, preferences: LearningPreferences, foregroundBusy: Boolean, batteryPercent: Int?, thermalLimited: Boolean): ReflectionMode = when {
        episode == null || preferences.mode == LearningPreference.OFF -> ReflectionMode.NONE
        preferences.mode == LearningPreference.SUGGEST && preferences.modelReflection && preferences.suggestMemories && episode.toolIds.size >= 3 && !foregroundBusy && !thermalLimited && (batteryPercent ?: 0) > 10 -> ReflectionMode.MODEL
        else -> ReflectionMode.DETERMINISTIC
    }
}
enum class PersonalSuggestionKind { MEMORY, PROCEDURE, AUTOMATION, CAPABILITY_SETUP }
enum class PersonalSuggestionReason { REPEATED_WORKFLOW, REPEATED_EXPLICIT_PREFERENCE, MEMORY_CONFLICT, MISSING_CAPABILITY, REPEATED_MANUAL_ROUTINE }
data class PersonalSuggestion(val id: String, val fingerprint: String, val kind: PersonalSuggestionKind, val title: String, val content: String,
    val evidenceCount: Int, val firstObserved: Instant, val lastObserved: Instant, val confidence: Float,
    val reason: PersonalSuggestionReason, val targetId: String? = null, val tools: List<String> = emptyList()) {
    init {
        require(id.length in 1..200 && fingerprint.length in 1..200 && title.length <= 80 && content.length <= 600)
        require(evidenceCount in 1..1000000 && confidence.isFinite() && confidence in 0f..1f && tools.size <= 12)
        require(tools.all { it.length in 1..200 } && (targetId?.length ?: 0) <= 200 && lastObserved >= firstObserved)
    }
}
data class SuggestionBudget(val maxPending: Int = 20, val maxNewPerDay: Int = 3, val cooldownDays: Long = 30, val repeatThreshold: Int = 3, val recentEpisodes: Int = 30, val recentDays: Long = 30)
object PersonalSuggestionPolicy {
    // All ordinary suggestions remain in Inbox; Home is a passive count, never speech.
    fun showHome(level: com.halo.runtime.cognition.attention.AttentionInitiativeLevel) = level != com.halo.runtime.cognition.attention.AttentionInitiativeLevel.QUIET
}
data class ManualRoutinePattern(val routineId: String, val completedCount: Int, val safe: Boolean)
data class CapabilityPattern(val capabilityId: String, val failureCount: Int, val setupVerified: Boolean)
class PersonalSuggestionEngine(private val budget: SuggestionBudget = SuggestionBudget()) {
    fun suggest(episodes: List<Episode>, preferences: LearningPreferences, knownProcedureTools: Set<List<String>>,
        manualRoutines: List<ManualRoutinePattern> = emptyList(), capabilities: List<CapabilityPattern> = emptyList()): List<PersonalSuggestion> {
        if(preferences.mode != LearningPreference.SUGGEST) return emptyList()
        val now = Instant.now(); val recent = episodes.filter { it.endedAt >= now.minusSeconds(budget.recentDays * 86400) }.take(budget.recentEpisodes)
        val result = mutableListOf<PersonalSuggestion>()
        if(preferences.suggestProcedures) recent.filter { it.outcome == EpisodeOutcome.SUCCESS && !it.unknownCompletion && it.workflow != null }
            .groupBy { requireNotNull(it.workflow).value }.forEach { (signature, matches) ->
                val tools = requireNotNull(matches.first().workflow).tools
                if(matches.size >= budget.repeatThreshold && tools !in knownProcedureTools) result += PersonalSuggestion("procedure:$signature", "procedure:$signature", PersonalSuggestionKind.PROCEDURE,
                    "Save a repeated workflow?", "You've completed this sequence ${matches.size} times recently. Review its steps before saving.", matches.size,
                    matches.minOf { it.endedAt }, matches.maxOf { it.endedAt }, (0.5f + matches.size * 0.05f).coerceAtMost(0.9f), PersonalSuggestionReason.REPEATED_WORKFLOW, tools = tools)
            }
        if(preferences.suggestAutomations) manualRoutines.filter { it.safe && it.completedCount >= budget.repeatThreshold }.forEach { pattern ->
            result += PersonalSuggestion("automation:${pattern.routineId}", "automation:${pattern.routineId}", PersonalSuggestionKind.AUTOMATION,
                "Review a repeated manual routine", "You've completed this manual routine ${pattern.completedCount} times recently. Choose when it should run and review its grant.", pattern.completedCount, now, now, 0.7f, PersonalSuggestionReason.REPEATED_MANUAL_ROUTINE, pattern.routineId)
        }
        capabilities.filter { it.setupVerified && it.failureCount >= budget.repeatThreshold }.forEach { pattern ->
            result += PersonalSuggestion("capability:${pattern.capabilityId}", "capability:${pattern.capabilityId}", PersonalSuggestionKind.CAPABILITY_SETUP,
                "Review capability setup", "This capability was unavailable for ${pattern.failureCount} recent requests. Review setup; nothing downloads automatically.", pattern.failureCount, now, now, 0.7f, PersonalSuggestionReason.MISSING_CAPABILITY, pattern.capabilityId)
        }
        return result.take(budget.maxNewPerDay)
    }
}
