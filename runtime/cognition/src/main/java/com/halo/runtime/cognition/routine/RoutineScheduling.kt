package com.halo.runtime.cognition.routine

import java.time.Instant
import kotlinx.coroutines.flow.Flow

enum class RoutineRunState { SCHEDULED, RUNNING, COMPLETED, FAILED, PARTIAL, SKIPPED, CANCELLED, INTERRUPTED, BLOCKED }
data class RoutineRunSummary(val id: String, val routineId: RoutineId, val agentRunId: String, val triggerAt: Instant, val state: RoutineRunState,
    val source: String = "SCHEDULED", val actionsCompleted: Int = 0, val actionsTotal: Int = 0,
    val finishedAt: Instant? = null, val reason: String? = null, val deliveryOutcome: String? = null, val coalescedCount: Int = 1)
interface RoutineRepository {
    fun observeRoutines(): Flow<List<Routine>>
    suspend fun get(id: RoutineId): Routine?
    suspend fun upsert(routine: Routine)
    suspend fun setEnabled(id: RoutineId, enabled: Boolean)
    suspend fun delete(id: RoutineId)
}
sealed interface RoutineScheduleResult {
    data class Scheduled(val at: Instant) : RoutineScheduleResult
    data object EventDriven : RoutineScheduleResult
    data class Blocked(val reason: String) : RoutineScheduleResult
}
interface RoutineScheduler {
    suspend fun schedule(routine: Routine): RoutineScheduleResult
    suspend fun cancel(routineId: RoutineId)
    suspend fun reconcile()
}
/** Zone rules resolve gaps forward and select the earlier overlap, once per local date. */
fun RoutineTrigger.nextOccurrence(after: Instant): Instant? = when (this) {
    is RoutineTrigger.Once -> at.takeIf { it > after }
    is RoutineTrigger.AtTime -> (0L..8L).asSequence().map { after.atZone(zoneId).toLocalDate().plusDays(it) }
        .filter { days.isEmpty() || it.dayOfWeek in days }
        .map { it.atTime(localTime).atZone(zoneId).withEarlierOffsetAtOverlap().toInstant() }
        .firstOrNull { it > after }
    else -> null
}
enum class AutomationPermission { DISALLOWED, ALLOWED_WITH_ROUTINE_GRANT, REQUIRES_INTERACTIVE_APPROVAL }
interface ToolAutomationPolicy { fun policyFor(toolId: String): AutomationPermission }
class DefaultToolAutomationPolicy(private val descriptor: (String) -> com.halo.runtime.cognition.tools.ToolDescriptor?) : ToolAutomationPolicy {
    override fun policyFor(toolId: String) = descriptor(toolId)?.automation?.permission ?: AutomationPermission.DISALLOWED
}
