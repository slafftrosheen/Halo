package com.halo.app.data.routine

import com.halo.runtime.cognition.routine.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ManagedRoutineSnapshot(val id: RoutineId, val name: String, val routine: Routine?, val sourceVersion: String)

class RoomRoutineRepository(val dao: RoutineDao) : RoutineRepository {
    private val managementMutex = Mutex()
    private val lifetime = java.util.UUID.randomUUID().toString()
    private val managementRevision = java.util.concurrent.atomic.AtomicLong()
    private val codec = RoutineCodec()
    val migrationErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    override fun observeRoutines() = dao.observeRoutines().map { rows -> rows.mapNotNull { decode(it) } }
    /** Home projection must not disable malformed routines while observing. */
    fun observeRoutinesReadOnly() = dao.observeRoutines().map { rows -> rows.mapNotNull { decode(it, readOnly = true) } }
    fun observeRecentRuns() = dao.observeRecentRuns().map { rows -> rows.map { it.toSummary() } }
    suspend fun runResult(id: String) = dao.run(id)?.toSummary()
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
        dao.upsert(entity(routine)); managementRevision.incrementAndGet()
        migrationErrors.value = migrationErrors.value - routine.id.value
    }
    private fun entity(routine: Routine): RoutineEntity {
        val r = routine
        return RoutineEntity(r.id.value, r.name, r.enabled, codec.encodeTrigger(r.trigger),
            codec.encodeConditions(r.conditions), codec.encodeActions(r.actions), 1,
            r.createdAt.toEpochMilli(), r.updatedAt.toEpochMilli(), r.lastRunAt?.toEpochMilli(), r.nextRunAt?.toEpochMilli(), codec.encodeDelivery(r.delivery), r.grantDigest, r.minimumIntervalSeconds)
    }
    override suspend fun setEnabled(id: RoutineId, enabled: Boolean) { dao.setEnabled(id.value, enabled); managementRevision.incrementAndGet() }
    override suspend fun delete(id: RoutineId) { dao.delete(id.value); managementRevision.incrementAndGet(); migrationErrors.value = migrationErrors.value - id.value }
    suspend fun managementList(): List<ManagedRoutineSnapshot> = managementMutex.withLock {
        dao.all().sortedBy { it.id }.map { snapshot(it) }
    }
    suspend fun managementGet(id: RoutineId): ManagedRoutineSnapshot? = managementMutex.withLock { dao.get(id.value)?.let { snapshot(it) } }
    suspend fun managementSave(id: RoutineId, sourceVersion: String, replacement: Routine,
        commit: (suspend (suspend () -> Unit) -> Unit)? = null): Boolean = managementMutex.withLock {
        require(replacement.id == id)
        val old = dao.get(id.value) ?: return@withLock false
        if (version(old) != sourceVersion) return@withLock false
        var saved = false
        val persist: suspend () -> Unit = {
            saved = dao.compareAndSet(old, entity(replacement))
            check(saved) { "Automation changed during save" }
        }
        if (commit == null) persist() else commit(persist)
        if (saved) { managementRevision.incrementAndGet(); migrationErrors.value = migrationErrors.value - id.value }
        saved
    }
    suspend fun managementCreate(routine: Routine, commit: (suspend (suspend () -> Unit) -> Unit)? = null): Boolean = managementMutex.withLock {
        if (dao.get(routine.id.value) != null) return@withLock false
        var saved = false
        val persist: suspend () -> Unit = { dao.upsert(entity(routine)); saved = true }
        if (commit == null) persist() else commit(persist)
        if (saved) managementRevision.incrementAndGet()
        saved
    }
    suspend fun managementDelete(id: RoutineId, sourceVersion: String): Boolean = managementMutex.withLock {
        val old = dao.get(id.value) ?: return@withLock false
        if (version(old) != sourceVersion || !dao.compareAndSet(old, null)) return@withLock false
        managementRevision.incrementAndGet(); migrationErrors.value = migrationErrors.value - id.value; true
    }
    suspend fun managementDisable(id: RoutineId, sourceVersion: String): Boolean = managementMutex.withLock {
        val old = dao.get(id.value) ?: return@withLock false
        if (version(old) != sourceVersion || !dao.compareAndSet(old, old.copy(enabled = false))) return@withLock false
        managementRevision.incrementAndGet(); true
    }
    /** Consumes only process-local semantic authority, without changing scheduled occurrence revisions. */
    suspend fun managementClaimRun(id: RoutineId, sourceVersion: String): Routine? = managementMutex.withLock {
        val old = dao.get(id.value) ?: return@withLock null
        if (version(old) != sourceVersion || !dao.matches(old)) return@withLock null
        val routine = decode(old, readOnly = true) ?: return@withLock null
        managementRevision.incrementAndGet(); routine
    }
    private suspend fun snapshot(row: RoutineEntity) = ManagedRoutineSnapshot(RoutineId(row.id), row.name, decode(row, readOnly = true), version(row))
    private fun version(row: RoutineEntity): String = "routine:" + java.security.MessageDigest.getInstance("SHA-256")
        .digest("$lifetime|${managementRevision.get()}|$row".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

internal fun RoutineRunEntity.toSummary() = RoutineRunSummary(id, RoutineId(routineId), agentRunId, Instant.ofEpochMilli(triggerAt),
    runCatching { RoutineRunState.valueOf(state) }.getOrDefault(RoutineRunState.INTERRUPTED), source, actionsCompleted, actionsTotal,
    finishedAt?.let(Instant::ofEpochMilli), reason, deliveryOutcome, coalescedCount)
