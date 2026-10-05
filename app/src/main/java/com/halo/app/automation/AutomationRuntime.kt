package com.halo.app.automation

import com.halo.app.data.routine.RoomRoutineRepository
import com.halo.runtime.cognition.routine.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import android.util.Log

/** A single bounded sequential consumer: events cannot fan out into unbounded agent jobs. */
class AutomationRuntime(val registry: AutomationSignalRegistry, private val repository: RoomRoutineRepository,
    private val executor: RoutineExecutor) {
    suspend fun observe(): Unit = coroutineScope {
        val configurationReady = kotlinx.coroutines.CompletableDeferred<Unit>()
        val indexed = kotlinx.coroutines.flow.MutableStateFlow<Map<AutomationSignalKind, List<Routine>>>(emptyMap())
        launch { repository.observeRoutines().collect { routines ->
            indexed.value = routines.filter { it.enabled }.take(100).groupBy { it.trigger.signalKind() }.mapNotNull { (kind, values) -> kind?.let { it to values } }.toMap()
            configurationReady.complete(Unit)
            val old = AutomationDiagnostics.snapshot.value
            AutomationDiagnostics.snapshot.value = old.copy(enabled = routines.count { it.enabled },
                scheduled = routines.count { it.trigger.reachability() == TriggerReachability.PLATFORM_SCHEDULED },
                eventDriven = routines.count { it.trigger.reachability() != TriggerReachability.PLATFORM_SCHEDULED },
                blocked = repository.migrationErrors.value.size + routines.count { it.grantDigest == null })
        } }
        launch { repository.observeRecentRuns().collect { runs ->
            AutomationDiagnostics.snapshot.value = AutomationDiagnostics.snapshot.value.copy(recentOccurrences = runs.take(10).map { "${it.id.takeLast(12)}:${it.source}:${it.state}:${it.actionsCompleted}/${it.actionsTotal}" })
        } }
        val seen = LinkedHashSet<String>()
        registry.signals.collect { signal ->
            configurationReady.await()
            if (signal.trust == AutomationTrust.INTERNAL_TEST || !seen.add(signal.id)) return@collect
            if (seen.size > 512) seen.remove(seen.first())
            Log.i("HALO_AUTOMATION_SIGNAL", "kind=${signal.kind} source=${signal.source} id=${signal.id.take(8)} time=${signal.occurredAt} trust=${signal.trust}")
            indexed.value[signal.kind].orEmpty().forEach { r ->
                val match = RoutineTriggerMatcher.matches(r.trigger, signal)
                Log.i("HALO_ROUTINE_MATCH", "routine=${r.id.value.take(8)} signal=${signal.id.take(8)} matched=$match")
                if (match) {
                    try { executor.execute(RoutineOccurrence.event(r, signal)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { Log.w("HALO_ROUTINE_MATCH", "routine=${r.id.value.take(8)} occurrence could not be processed") }
                }
            }
        }
    }
    suspend fun preview(signal: AutomationSignal): List<String> {
        require(signal.trust == AutomationTrust.INTERNAL_TEST)
        val snapshot = executor.environmentSnapshot()
        return repository.all().take(100).map { r ->
            val match = RoutineTriggerMatcher.matches(r.trigger, signal)
            val conditions = r.conditions.map { RoutineConditionEvaluator.evaluate(it, snapshot).name }
            val eligible = runCatching { executor.validate(r) }.isSuccess
            "${r.name}: matches=$match; conditions=${conditions.joinToString()}; actions eligible=$eligible"
        }
    }
}
