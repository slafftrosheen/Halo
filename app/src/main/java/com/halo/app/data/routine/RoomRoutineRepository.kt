package com.halo.app.data.routine

import com.halo.runtime.cognition.routine.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class RoomRoutineRepository(val dao: RoutineDao) : RoutineRepository {
    private val codec = RoutineCodec()
    val migrationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observeRoutines() = dao.observeRoutines().map { rows -> rows.mapNotNull { decode(it) } }
    /** Home projection must not disable malformed routines while observing. */
    fun observeRoutinesReadOnly() = dao.observeRoutines().map { rows -> rows.mapNotNull { decode(it, readOnly = true) } }
    fun observeRecentRuns() = dao.observeRecentRuns().map { rows -> rows.map { row ->
        RoutineRunSummary(row.id, RoutineId(row.routineId), row.agentRunId, Instant.ofEpochMilli(row.triggerAt),
            runCatching { RoutineRunState.valueOf(row.state) }.getOrDefault(RoutineRunState.INTERRUPTED), row.source, row.actionsCompleted, row.actionsTotal,
            row.finishedAt?.let(Instant::ofEpochMilli), row.reason, row.deliveryOutcome, row.coalescedCount)
    } }
    suspend fun all() = dao.all().mapNotNull { decode(it) }
    override suspend fun get(id: RoutineId) = dao.get(id.value)?.let { decode(it) }
    private suspend fun decode(e: RoutineEntity, readOnly: Boolean = false): Routine? = try {
        Routine(RoutineId(e.id), e.name, e.enabled, codec.decodeTrigger(e.triggerJson, e.schemaVersion),
            codec.decodeConditions(e.conditionsJson, e.schemaVersion), codec.decodeActions(e.actionsJson, e.schemaVersion),
            Instant.ofEpochMilli(e.createdAt), Instant.ofEpochMilli(e.updatedAt), e.lastRunAt?.let(Instant::ofEpochMilli), e.nextRunAt?.let(Instant::ofEpochMilli), codec.decodeDelivery(e.deliveryJson), e.grantDigest, e.minimumIntervalSeconds)
    } catch (error: java.time.DateTimeException) { if (!readOnly) invalid(e, error); null }
      catch (error: IllegalArgumentException) { if (!readOnly) invalid(e, error); null }
      catch (error: org.json.JSONException) { if (!readOnly) invalid(e, error); null }
      catch (error: IllegalStateException) { if (!readOnly) invalid(e, error); null }
    private suspend fun invalid(e: RoutineEntity, error: Exception) {
        if (e.enabled) dao.setEnabled(e.id, false)
        migrationErrors.value = migrationErrors.value + (e.id to "${e.name}: ${error.message}")
    }
    override suspend fun upsert(routine: Routine) {
        val r = routine
        dao.upsert(RoutineEntity(r.id.value, r.name, r.enabled, codec.encodeTrigger(r.trigger),
            codec.encodeConditions(r.conditions), codec.encodeActions(r.actions), 1,
            r.createdAt.toEpochMilli(), r.updatedAt.toEpochMilli(), r.lastRunAt?.toEpochMilli(), r.nextRunAt?.toEpochMilli(), codec.encodeDelivery(r.delivery), r.grantDigest, r.minimumIntervalSeconds))
        migrationErrors.value = migrationErrors.value - r.id.value
    }
    override suspend fun setEnabled(id: RoutineId, enabled: Boolean) = dao.setEnabled(id.value, enabled)
    override suspend fun delete(id: RoutineId) { dao.delete(id.value); migrationErrors.value = migrationErrors.value - id.value }
}
