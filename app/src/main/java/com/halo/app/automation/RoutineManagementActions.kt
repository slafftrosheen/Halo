package com.halo.app.automation

import com.halo.app.data.routine.*
import com.halo.runtime.cognition.routine.*
import java.time.Instant

data class RoutineManagementReceipt(val routineId: RoutineId, val snapshot: ManagedRoutineSnapshot?,
    val scheduling: String, val runId: String? = null, val runRecorded: Boolean = false, val schedulingReason: String? = null)

/** Shared editor/assistant commands. RoutineExecutor remains the only execution/automation-policy owner. */
class RoutineManagementActions(
    private val repository: RoomRoutineRepository,
    private val scheduler: RoutineScheduler,
    private val validateForSave: (Routine) -> Unit,
    private val validate: (Routine) -> Unit,
    private val grantSummary: (Routine) -> String,
    private val executeOccurrence: suspend (RoutineOccurrence) -> String?,
    private val now: () -> Instant = Instant::now,
) {
    suspend fun list() = repository.managementList()
    suspend fun get(id: RoutineId) = repository.managementGet(id)
    suspend fun runStatus(runId: String) = repository.runResult(runId)
    fun review(routine: Routine): String { validateForSave(routine); return grantSummary(routine) }
    fun reviewRun(routine: Routine): String { validate(routine); requireGrant(routine); return grantSummary(routine) }
    fun reviewEnable(routine: Routine): String { validateForSave(routine); requireGrant(routine); return grantSummary(routine) }

    suspend fun save(routine: Routine, expectedVersion: String? = null,
        commit: (suspend (suspend () -> Unit) -> Unit)? = null): RoutineManagementReceipt? {
        val original = repository.managementGet(routine.id)
        if (expectedVersion != null && (original == null || original.sourceVersion != expectedVersion)) return null
        if (original != null && original.routine == null) return null
        if (expectedVersion == null && original?.routine?.updatedAt?.let { it != routine.updatedAt } == true) return null
        validateForSave(routine)
        (routine.trigger as? RoutineTrigger.Once)?.let { require(it.at > now()) { "Choose a future time" } }
        val approved = routine.copy(createdAt = original?.routine?.createdAt ?: routine.createdAt,
            updatedAt = nextRevision(original?.routine?.updatedAt), grantDigest = RoutineArguments.grant(routine))
        val committed = if (original == null) repository.managementCreate(approved, commit)
            else repository.managementSave(routine.id, original.sourceVersion, approved, commit)
        if (!committed) return null
        return schedule(approved)
    }

    suspend fun setEnabled(id: RoutineId, enabled: Boolean, expectedVersion: String? = null): RoutineManagementReceipt? {
        val current = repository.managementGet(id) ?: return null
        if (expectedVersion != null && current.sourceVersion != expectedVersion) return null
        val saved = current.routine
        if (saved == null) {
            if (enabled || !repository.managementDisable(id, current.sourceVersion)) return null
            scheduler.cancel(id)
            return RoutineManagementReceipt(id, repository.managementGet(id), "CANCEL_REQUESTED")
        }
        if (enabled) {
            validateForSave(saved)
            require(saved.grantDigest != null && saved.grantDigest == RoutineArguments.grant(saved)) {
                "Open and save this automation to approve its actions"
            }
        }
        val changed = saved.copy(enabled = enabled, updatedAt = nextRevision(saved.updatedAt))
        if (!repository.managementSave(id, current.sourceVersion, changed)) return null
        return if (enabled) schedule(changed) else {
            scheduler.cancel(id)
            RoutineManagementReceipt(id, repository.managementGet(id), "CANCEL_REQUESTED")
        }
    }

    suspend fun delete(id: RoutineId, expectedVersion: String? = null): RoutineManagementReceipt? {
        val current = repository.managementGet(id) ?: return null
        if (expectedVersion != null && current.sourceVersion != expectedVersion) return null
        if (!repository.managementDelete(id, current.sourceVersion)) return null
        scheduler.cancel(id)
        return RoutineManagementReceipt(id, repository.managementGet(id), "CANCEL_REQUESTED")
    }

    suspend fun runNow(id: RoutineId, expectedVersion: String? = null): RoutineManagementReceipt? {
        val current = repository.managementGet(id) ?: return null
        if (expectedVersion != null && current.sourceVersion != expectedVersion) return null
        val saved = current.routine ?: return null
        validate(saved)
        require(saved.grantDigest != null && saved.grantDigest == RoutineArguments.grant(saved)) { "Review this automation before running it" }
        val exact = repository.managementClaimRun(id, current.sourceVersion) ?: return null
        val occurrence = RoutineOccurrence.manual(exact, now())
        executeOccurrence(occurrence)
        return RoutineManagementReceipt(id, repository.managementGet(id), "DISPATCHED", occurrence.id,
            repository.runResult(occurrence.id) != null)
    }

    private suspend fun schedule(routine: Routine): RoutineManagementReceipt {
        val current = requireNotNull(repository.get(routine.id)) { "Automation no longer saved" }
        check(current.updatedAt == routine.updatedAt) { "Automation changed before scheduling" }
        val result = scheduler.schedule(current)
        val state = when (result) {
            is RoutineScheduleResult.Scheduled -> "SCHEDULED"
            is RoutineScheduleResult.EventDriven -> "EVENT_DRIVEN"
            is RoutineScheduleResult.Blocked -> "BLOCKED"
        }
        return RoutineManagementReceipt(routine.id, repository.managementGet(routine.id), state,
            schedulingReason = (result as? RoutineScheduleResult.Blocked)?.reason)
    }
    private fun nextRevision(previous: Instant?): Instant = Instant.ofEpochMilli(now().toEpochMilli()).let { current ->
        if (previous != null && !current.isAfter(previous)) previous.plusMillis(1) else current
    }
    private fun requireGrant(routine: Routine) {
        require(routine.grantDigest != null && routine.grantDigest == RoutineArguments.grant(routine)) { "Review this automation before running it" }
    }
}
