package com.halo.app.data.routine

import com.halo.runtime.cognition.routine.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

class RoomRoutineRepository(val dao: RoutineDao) : RoutineRepository {
    private val codec = RoutineCodec()
    val migrationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observeRoutines() = dao.observeRoutines().map { rows -> rows.mapNotNull { decode(it) } }
    fun observeRecentRuns() = dao.observeRecentRuns().map { rows -> rows.map { row ->
        RoutineRunSummary(row.id, RoutineId(row.routineId), row.agentRunId, Instant.ofEpochMilli(row.triggerAt),
            runCatching { RoutineRunState.valueOf(row.state) }.getOrDefault(RoutineRunState.INTERRUPTED))
    } }
    suspend fun all() = dao.all().mapNotNull { decode(it) }
    override suspend fun get(id: RoutineId) = dao.get(id.value)?.let { decode(it) }
    private suspend fun decode(e: RoutineEntity): Routine? = try {
        Routine(RoutineId(e.id), e.name, e.enabled, codec.decodeTrigger(e.triggerJson, e.schemaVersion),
            codec.decodeConditions(e.conditionsJson, e.schemaVersion), codec.decodeActions(e.actionsJson, e.schemaVersion),
            Instant.ofEpochMilli(e.createdAt), Instant.ofEpochMilli(e.updatedAt), e.lastRunAt?.let(Instant::ofEpochMilli), e.nextRunAt?.let(Instant::ofEpochMilli))
    } catch (error: java.time.DateTimeException) { invalid(e, error); null }
      catch (error: IllegalArgumentException) { invalid(e, error); null }
      catch (error: org.json.JSONException) { invalid(e, error); null }
      catch (error: IllegalStateException) { invalid(e, error); null }
    private suspend fun invalid(e: RoutineEntity, error: Exception) {
        if (e.enabled) dao.setEnabled(e.id, false, System.currentTimeMillis())
        migrationErrors.value = migrationErrors.value + (e.id to "${e.name}: ${error.message}")
    }
    override suspend fun upsert(routine: Routine) {
        val r = routine
        dao.upsert(RoutineEntity(r.id.value, r.name, r.enabled, codec.encodeTrigger(r.trigger),
            codec.encodeConditions(r.conditions), codec.encodeActions(r.actions), 1,
            r.createdAt.toEpochMilli(), r.updatedAt.toEpochMilli(), r.lastRunAt?.toEpochMilli(), r.nextRunAt?.toEpochMilli()))
        migrationErrors.value = migrationErrors.value - r.id.value
    }
    override suspend fun setEnabled(id: RoutineId, enabled: Boolean) = dao.setEnabled(id.value, enabled, System.currentTimeMillis())
    override suspend fun delete(id: RoutineId) { dao.delete(id.value); migrationErrors.value = migrationErrors.value - id.value }
}
