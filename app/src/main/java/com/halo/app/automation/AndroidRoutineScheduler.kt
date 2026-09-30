package com.halo.app.automation

import android.content.Context
import androidx.work.*
import com.halo.app.HaloApplication
import com.halo.app.data.routine.RoomRoutineRepository
import com.halo.app.di.ManualDependencyGraph
import com.halo.runtime.cognition.routine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.Duration
import java.util.concurrent.TimeUnit

class AndroidRoutineScheduler(private val context: Context, private val repository: RoomRoutineRepository,
    private val validate: (Routine) -> Unit) : RoutineScheduler {
    private val work get() = WorkManager.getInstance(context)
    override suspend fun schedule(routine: Routine): RoutineScheduleResult = withContext(Dispatchers.IO) {
        val r = routine
        if (!r.enabled) { cancel(r.id); return@withContext RoutineScheduleResult.Blocked("Disabled") }
        try { validate(r) } catch (e: Exception) {
            repository.setEnabled(r.id, false); cancel(r.id)
            repository.migrationErrors.value = repository.migrationErrors.value + (r.id.value to (e.message ?: "Invalid routine"))
            return@withContext RoutineScheduleResult.Blocked(e.message ?: "Invalid routine")
        }
        val latest = repository.get(r.id)
        if (latest == null || !latest.enabled || latest.updatedAt != r.updatedAt) {
            return@withContext RoutineScheduleResult.Blocked("Routine changed")
        }
        val now = Instant.now()
        val at = r.trigger.nextOccurrence(now)
        if (at == null) {
            cancel(r.id)
            if (r.trigger is RoutineTrigger.Once) {
                repository.setEnabled(r.id, false)
                return@withContext RoutineScheduleResult.Blocked("Expired")
            }
            return@withContext RoutineScheduleResult.EventDriven
        }
        val request = OneTimeWorkRequestBuilder<RoutineWorker>()
            .setInitialDelay(Duration.between(now, at).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("routine" to r.id.value, "occurrence" to at.toEpochMilli(), "revision" to r.updatedAt.toEpochMilli()))
            .addTag(TAG).addTag("routine:${r.id.value}").addTag("revision:${r.updatedAt.toEpochMilli()}").build()
        work.enqueueUniqueWork("halo.routine.${r.id.value}", ExistingWorkPolicy.REPLACE, request).result.get()
        repository.dao.nextRun(r.id.value, at.toEpochMilli())
        RoutineScheduleResult.Scheduled(at)
    }
    override suspend fun cancel(routineId: RoutineId) = withContext(Dispatchers.IO) {
        work.cancelUniqueWork("halo.routine.${routineId.value}").result.get()
        repository.dao.nextRun(routineId.value, null)
    }
    override suspend fun reconcile() { reconcile(false) }
    suspend fun reconcile(clockChanged: Boolean) = withContext(Dispatchers.IO) {
        val routines = repository.all()
        val ids = routines.map { it.id.value }.toSet()
        val existing = work.getWorkInfosByTag(TAG).get()
        existing.filter { !it.state.isFinished }.forEach { info ->
            val id = info.tags.firstOrNull { it.startsWith("routine:") }?.removePrefix("routine:")
            if (id !in ids) work.cancelWorkById(info.id).result.get()
        }
        routines.forEach { r ->
            // Preserve overdue persisted work and running workers; bootstrap must not replace either.
            val pending = existing.any { !it.state.isFinished && "routine:${r.id.value}" in it.tags && "revision:${r.updatedAt.toEpochMilli()}" in it.tags }
            val running = existing.any { it.state == WorkInfo.State.RUNNING && "routine:${r.id.value}" in it.tags }
            if (!r.enabled) cancel(r.id) else if (!pending || (clockChanged && !running && r.trigger is RoutineTrigger.AtTime)) schedule(r)
        }
    }
    companion object { const val TAG = "halo.routines" }
}
class RoutineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        executeOccurrence()
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
      catch (e: Exception) {
        android.util.Log.e("RoutineWorker", "Routine could not complete; no automatic replay", e)
        Result.failure()
    }
    private suspend fun executeOccurrence(): Result {
        val app = applicationContext as HaloApplication
        app.awaitAutomationBootstrap()
        val graph = ManualDependencyGraph
        val id = RoutineId(inputData.getString("routine") ?: return Result.failure())
        val routine = graph.routineRepository.get(id) ?: return Result.success()
        if (!routine.enabled || routine.updatedAt.toEpochMilli() != inputData.getLong("revision", -1)) return Result.success()
        val epoch = inputData.getLong("occurrence", -1)
        if (epoch < 0) return Result.failure()
        graph.routineExecutor.execute(RoutineOccurrence.scheduled(routine, Instant.ofEpochMilli(epoch)))
        val latest = graph.routineRepository.get(id)
        if (latest != null && latest.updatedAt == routine.updatedAt) {
            // Enqueue a successor, without cancelling this running worker via REPLACE.
            val next = latest.trigger.nextOccurrence(Instant.now())
            if (next == null && latest.trigger is RoutineTrigger.Once) {
                graph.routineRepository.setEnabled(id, false)
                graph.routineRepository.dao.nextRun(id.value, null)
            } else if (next != null && latest.enabled) {
                val request = OneTimeWorkRequestBuilder<RoutineWorker>()
                    .setInitialDelay(Duration.between(Instant.now(), next).toMillis().coerceAtLeast(0), TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf("routine" to id.value, "occurrence" to next.toEpochMilli(), "revision" to latest.updatedAt.toEpochMilli()))
                    .addTag(AndroidRoutineScheduler.TAG).addTag("routine:${id.value}").addTag("revision:${latest.updatedAt.toEpochMilli()}").build()
                withContext(Dispatchers.IO) {
                    WorkManager.getInstance(applicationContext).enqueueUniqueWork("halo.routine.${id.value}", ExistingWorkPolicy.APPEND_OR_REPLACE, request).result.get()
                }
                graph.routineRepository.dao.nextRun(id.value, next.toEpochMilli())
            }
        }
        return Result.success()
    }
}
