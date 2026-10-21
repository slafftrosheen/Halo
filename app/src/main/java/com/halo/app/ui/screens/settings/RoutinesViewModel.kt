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
data class RoutineTrialState(val draftKey: String? = null, val running: Boolean = false,
    val result: RoutineRunSummary? = null, val error: String? = null)

internal fun routineTrialKey(r: Routine): String = r.id.value + ":" + r.name + ":" + RoutineArguments.grant(r)

class RoutinesViewModel(private val repository: RoomRoutineRepository, private val scheduler: RoutineScheduler,
    private val executor: RoutineExecutor) : ViewModel() {
    private val commands = RoutineCommands(repository, scheduler, executor::execute)
    private val mutableTrial = MutableStateFlow(RoutineTrialState())
    val trial = mutableTrial.asStateFlow()
    private var trialJob: Job? = null
    fun tryOnce(draft: Routine) {
        if (mutableTrial.value.running || saving.value) return
        val key = routineTrialKey(draft)
        val runId = "trial:${UUID.randomUUID()}"
        mutableTrial.value = RoutineTrialState(key, running = true)
        trialJob = viewModelScope.launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
            var failure: String? = null
            try { executor.executeTrial(draft, runId) }
            catch (e: CancellationException) { failure = "Trial stopped before a result was recorded."; throw e }
            catch (_: Exception) { failure = "Trial unavailable. Check the actions and try again." }
            finally { withContext(NonCancellable) {
                val receipt = runCatching { executor.runResult(runId) }.getOrNull()
                mutableTrial.value = RoutineTrialState(key, result = receipt,
                    error = if (receipt == null) failure ?: "Trial unavailable. Check the actions and try again." else null)
            } }
        }
    }
    fun stopTrial() { trialJob?.cancel() }
    val running = MutableStateFlow<Set<RoutineId>>(emptySet())
    fun review(routine: Routine): String { executor.validate(routine); return executor.grantSummary(routine) }
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
        if (saving.value || mutableTrial.value.running) return
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
