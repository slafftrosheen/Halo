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
    val schedulingStatus: String = "WorkManager · execution may be delayed by Android", val loading: Boolean = true, val error: String? = null, val blockedRoutines: Map<String, String> = emptyMap())
class RoutinesViewModel(private val repository: RoomRoutineRepository, private val scheduler: AndroidRoutineScheduler,
    private val executor: RoutineExecutor) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    val result = MutableStateFlow<String?>(null)
    val uiState = combine(repository.observeRoutines(), repository.observeRecentRuns(), repository.migrationErrors, error) { routines, runs, migration, failure ->
        RoutinesUiState(routines, runs, loading = false, error = failure, blockedRoutines = migration)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RoutinesUiState())
    private fun act(block: suspend () -> Unit) { viewModelScope.launch(Dispatchers.IO) {
        try { error.value = null; block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "Operation failed" }
    } }
    fun save(r: Routine, saved: () -> Unit) = act {
        executor.validate(r)
        (r.trigger as? RoutineTrigger.Once)?.let { require(it.at > Instant.now()) { "Choose a future time" } }
        repository.upsert(r.copy(updatedAt = Instant.now()))
        val schedule = scheduler.schedule(requireNotNull(repository.get(r.id)))
        if (schedule is RoutineScheduleResult.Blocked && r.enabled) error.value = schedule.reason
        withContext(Dispatchers.Main) { saved() }
    }
    fun enable(id: RoutineId, enabled: Boolean) = act {
        val r = requireNotNull(repository.get(id))
        if (enabled) executor.validate(r)
        repository.setEnabled(id, enabled)
        if (enabled) {
            val result = scheduler.schedule(requireNotNull(repository.get(id)))
            if (result is RoutineScheduleResult.Blocked) error.value = result.reason
        } else scheduler.cancel(id)
    }
    fun delete(id: RoutineId) = act { repository.delete(id); scheduler.cancel(id) }
    fun runNow(id: RoutineId) = act { result.value = executor.execute(id, Instant.now(), manual = true) ?: "Occurrence already recorded" }
    fun summarizeNow() = act {
        val now = Instant.now()
        val r = Routine(RoutineId("manual-${UUID.randomUUID()}"), "Notification digest", false,
            RoutineTrigger.Once(now), actions = listOf(RoutineAction("halo_notification_digest", "{\"window_minutes\":480}")), createdAt = now, updatedAt = now)
        executor.validate(r)
        repository.upsert(r)
        try { result.value = executor.execute(r.id, now, manual = true) }
        finally { repository.delete(r.id) }
    }
}
