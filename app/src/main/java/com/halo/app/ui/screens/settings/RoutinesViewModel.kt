package com.halo.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.halo.app.automation.*
import com.halo.app.data.routine.*
import com.halo.runtime.cognition.routine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.util.UUID

data class RoutinesUiState(val routines: List<Routine> = emptyList(), val recentRuns: List<RoutineRunSummary> = emptyList(),
    val schedulingStatus: String = "WorkManager · execution may be delayed by Android", val loading: Boolean = true, val error: String? = null, val blockedRoutines: Map<String, String> = emptyMap(), val saving: Boolean = false)
class RoutinesViewModel(private val repository: RoomRoutineRepository, private val scheduler: RoutineScheduler,
    private val executor: RoutineExecutor) : ViewModel() {
    private val commands = RoutineCommands(repository, scheduler, executor::execute)
    val running = MutableStateFlow<Set<RoutineId>>(emptySet())
    fun review(routine: Routine): String { executor.validateForSave(routine); return executor.grantSummary(routine) }
    val actionDescriptors get() = executor.actionDescriptors()
    private val error = MutableStateFlow<String?>(null)
    val result = MutableStateFlow<String?>(null)
    private val saving = MutableStateFlow(false)
    val uiState = combine(repository.observeRoutines(), repository.observeRecentRuns(), repository.migrationErrors, error, saving) { routines, runs, migration, failure, savePending ->
        val unavailableActions = routines.mapNotNull { routine ->
            try { executor.validate(routine); null }
            catch (e: IllegalArgumentException) { routine.id.value to (e.message ?: "Action unavailable") }
        }.toMap()
        RoutinesUiState(routines, runs, loading = false, error = failure, blockedRoutines = migration + unavailableActions, saving = savePending)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutinesUiState())
    private fun act(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) {
        try { error.value = null; block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "Operation failed" }
    } }
    fun save(r: Routine, saved: () -> Unit) {
        if (saving.value) return
        saving.value = true
        act { try {
        executor.validateForSave(r)
        (r.trigger as? RoutineTrigger.Once)?.let { require(it.at > Instant.now()) { "Choose a future time" } }
        repository.upsert(r.copy(updatedAt = Instant.now(), grantDigest = RoutineArguments.grant(r)))
        val schedule = scheduler.schedule(requireNotNull(repository.get(r.id)))
        if (schedule is RoutineScheduleResult.Blocked && r.enabled) error.value = schedule.reason
        withContext(Dispatchers.Main) { saved() }
        } finally { saving.value = false } }
    }
    fun enable(id: RoutineId, enabled: Boolean) = act {
        val r = requireNotNull(repository.get(id))
        if (enabled) {
            executor.validateForSave(r)
            require(r.grantDigest != null && r.grantDigest == RoutineArguments.grant(r)) { "Open and save this automation to approve its actions" }
        }
        repository.setEnabled(id, enabled)
        if (enabled) {
            val result = scheduler.schedule(requireNotNull(repository.get(id)))
            if (result is RoutineScheduleResult.Blocked) error.value = result.reason
        } else scheduler.cancel(id)
    }
    fun reconcile() = act { scheduler.reconcile() }
    fun delete(id: RoutineId) = act { commands.delete(id) }
    fun runNow(id: RoutineId) {
        if (id in running.value) return
        running.update { it + id }
        act {
            try { commands.runNow(id) }
            finally { running.update { it - id } }
        }
    }
    fun summarizeNow() = act {
        val now = Instant.now()
        val r = Routine(RoutineId("manual-${UUID.randomUUID()}"), "Notification digest", false,
            RoutineTrigger.Once(now), actions = listOf(RoutineAction("halo_notification_digest", "{\"window_minutes\":480}")), createdAt = now, updatedAt = now)
        executor.validate(r)
        repository.upsert(r.copy(grantDigest = RoutineArguments.grant(r)))
        try { result.value = executor.execute(r.id, now, manual = true) }
        finally { repository.delete(r.id) }
    }
}
