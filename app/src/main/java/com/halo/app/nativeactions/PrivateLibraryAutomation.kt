package com.halo.app.nativeactions

import android.util.Log
import com.halo.app.automation.AndroidRoutineScheduler
import com.halo.app.automation.AutomationPrivacyFence
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectEntity
import com.halo.app.data.routine.RoutineRunEntity
import com.halo.app.data.workspace.privateLibraryClaim
import com.halo.runtime.cognition.attention.AttentionController
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal data class PrivateAutomationOwner(val id: String, val type: String)

/** Owns the Room automation definitions and evidence, and coordinates their Android dispatch owners. */
internal class PrivateLibraryAutomation(
    private val database: AppDatabase,
    private val scheduler: AndroidRoutineScheduler,
    private val attention: AttentionController? = null,
    private val cancelNotice: (String) -> Unit = {},
    private val withdrawScreen: (Set<String>) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val routines get() = database.routineDao()
    private val watches get() = database.continuationWatchDao()

    suspend fun relatedOwners(sourceId: String): List<PrivateAutomationOwner> {
        if (sourceId.isBlank()) return emptyList()
        val routineIds = routines.privateCandidates(sourceId)
        val watchIds = watches.privateCandidates(sourceId)
        check(routineIds.size + watchIds.size <= PrivateLibraryCodec.maxItems) { "Too many linked automations" }
        return routineIds.map { PrivateAutomationOwner(it, ROUTINE) } + watchIds.map { PrivateAutomationOwner(it, WATCH) }
    }

    suspend fun captureRoutine(id: String): PrivateLibraryItem? {
        val routine = routines.get(id)
        val rows = routineRuns(id)
        if (routine == null && rows.isEmpty()) return null
        val createdAt = routine?.createdAt ?: rows.first().triggerAt
        val updatedAt = routine?.updatedAt ?: rows.maxOf { it.finishedAt ?: it.triggerAt }
        return item(id, ROUTINE, routine?.name ?: "Automation history", createdAt, updatedAt,
            JSONObject().put("definition", routine?.let(PrivateLibraryAutomationCodec::encode))
                .put("runs", JSONArray(rows.map(PrivateLibraryAutomationCodec::encode))))
    }

    suspend fun captureWatch(id: String): PrivateLibraryItem? {
        val watch = watches.getWatchById(id) ?: return null
        val title = watch.presentationText?.takeIf(String::isNotBlank)?.take(160) ?: "Continuation watch"
        return item(id, WATCH, title, watch.createdAt, watch.matchedAt ?: watch.createdAt,
            PrivateLibraryAutomationCodec.encode(watch))
    }

    fun runIds(item: PrivateLibraryItem): Set<String> = when (item.header.type) {
        ROUTINE -> runs(item).mapTo(linkedSetOf()) { it.agentRunId }
        WATCH -> watch(item).let { row ->
            val behaviorRun = if (row.behaviorType in setOf("MAKE_RUN_RESUMABLE", "RECONCILE_RUN"))
                JSONObject(row.behaviorJson).getString("runId") else null
            setOfNotNull(row.runId, behaviorRun)
        }
        else -> error("Unknown automation snapshot")
    }

    fun ownedIds(item: PrivateLibraryItem): Set<String> = if (item.header.type == ROUTINE)
        runs(item).mapTo(linkedSetOf()) { it.id } else emptySet()

    fun preview(item: PrivateLibraryItem): String = when (item.header.type) {
        ROUTINE -> listOf(item.header.title, "Run history: ${runs(item).size}").joinToString("\n")
        WATCH -> watch(item).let { row -> row.presentationText ?: JSONObject(row.behaviorJson).optString("message", item.header.title) }
        else -> error("Unknown automation snapshot")
    }

    suspend fun <T> withWithdrawal(items: List<PrivateLibraryItem>, seal: suspend () -> T): T =
        AutomationPrivacyFence.withOwners(items.mapTo(linkedSetOf()) { ownerKey(it) }) {
            try {
                if (attention == null) seal() else attention.withDeliveryBarrier(seal)
            } finally {
                // On rollback the existing work and delivery projections remain in place.
                // Use the durable claim in case cancellation raced delivery of the commit result.
                withContext(NonCancellable) {
                    try { withdrawCommittedProjections(items) }
                    catch (_: Exception) { Log.w("HALO_PRIVATE", "Automation projections will be cleared at reconciliation") }
                }
            }
        }

    private suspend fun withdrawCommittedProjections(items: List<PrivateLibraryItem>) {
        val committed = items.filter { routines.isPrivateClaimed(privateLibraryClaim(it.header.id)) }
        val routineIds = committed.filter { it.header.type == ROUTINE }.mapTo(linkedSetOf()) { it.header.id }
        if (routineIds.isNotEmpty()) {
            try { withdrawScreen(routineIds) }
            catch (_: Exception) { Log.w("HALO_PRIVATE", "Withdrawn routine screens will be cleared at reconciliation") }
        }
        for (item in committed) {
            for (notice in noticeIds(item)) {
                try { cancelNotice(notice) }
                catch (_: Exception) { Log.w("HALO_PRIVATE", "Android could not retract an automation notification") }
            }
        }
        for (routineId in routineIds) {
            try { scheduler.cancelWithdrawnWork(routineId) }
            catch (_: Exception) { Log.w("HALO_PRIVATE", "Withdrawn routine work will be cleared at reconciliation") }
        }
    }

    suspend fun withdraw(item: PrivateLibraryItem) {
        val id = item.header.id
        when (item.header.type) {
            ROUTINE -> {
                val expected = routine(item)
                val history = runs(item)
                check(routines.get(id) == expected && routineRuns(id) == history) { "Automation changed during review" }
                check(routines.withdrawPrivateRuns(id) == history.size)
                if (expected != null) check(routines.compareAndSet(expected, null))
            }
            WATCH -> {
                check(watches.getWatchById(id) == watch(item)) { "Continuation watch changed during review" }
                check(watches.withdrawPrivateWatch(id) == 1)
            }
            else -> error("Unknown automation snapshot")
        }
        for (ids in noticeIds(item).chunked(400)) {
            database.attentionDao().withdrawPrivatePending(ids)
            database.attentionDao().withdrawPrivateReceipts(ids)
        }
    }

    suspend fun restore(item: PrivateLibraryItem) {
        val id = item.header.id
        when (item.header.type) {
            ROUTINE -> {
                check(routines.get(id) == null && routineRuns(id).isEmpty()) { "Automation restore conflict" }
                routine(item)?.let { row ->
                    // A released definition is useful immediately for review, while unattended authority
                    // must be granted from the normal editor. Old worker revisions cannot be replayed.
                    routines.insertRestored(row.copy(enabled = false, nextRunAt = null, grantDigest = null,
                        updatedAt = maxOf(clock(), Math.addExact(row.updatedAt, 1L))))
                }
                val history = runs(item).map { row ->
                    if (row.state == "RUNNING" || row.state == "SCHEDULED") row.copy(state = "INTERRUPTED") else row
                }
                if (history.isNotEmpty()) routines.insertRestoredRuns(history)
            }
            WATCH -> {
                val row = watch(item)
                val restored = if (row.status == "ACTIVE" || row.status == "MATCH_CLAIMED") row.copy(status = "CANCELLED") else row
                watches.insertRestored(restored)
            }
            else -> error("Unknown automation snapshot")
        }
    }

    private fun ownerKey(item: PrivateLibraryItem): String = when (item.header.type) {
        ROUTINE -> AutomationPrivacyFence.routine(item.header.id)
        WATCH -> AutomationPrivacyFence.watch(item.header.id)
        else -> error("Unknown automation snapshot")
    }

    private fun noticeIds(item: PrivateLibraryItem): List<String> = when (item.header.type) {
        ROUTINE -> runs(item).flatMap { row -> listOf("result:${row.id}") +
            listOf("COMPLETED", "FAILED", "INTERRUPTED").map { state -> "run:${row.agentRunId}:$state" }
        }
        WATCH -> listOf("watch:${item.header.id}")
        else -> error("Unknown automation snapshot")
    }

    private suspend fun routineRuns(id: String): List<RoutineRunEntity> = routines.privateRuns(id, maxRuns + 1).also {
        check(it.size <= maxRuns) { "Automation history is too large to lock as one item" }
    }

    private fun routine(item: PrivateLibraryItem) = requireNotNull(item.automation).optJSONObject("definition")
        ?.let { PrivateLibraryAutomationCodec.routine(item.header.id, it) }

    private fun runs(item: PrivateLibraryItem): List<RoutineRunEntity> {
        val rows = requireNotNull(item.automation).getJSONArray("runs")
        require(rows.length() <= maxRuns)
        return List(rows.length()) { PrivateLibraryAutomationCodec.run(item.header.id, rows.getJSONObject(it)) }
    }

    private fun watch(item: PrivateLibraryItem) = PrivateLibraryAutomationCodec.watch(item.header.id, requireNotNull(item.automation))

    private fun item(id: String, type: String, title: String, createdAt: Long, updatedAt: Long, content: JSONObject): PrivateLibraryItem {
        val header = WorkspaceObjectEntity(id, type, title, createdAt, updatedAt, "AUTOMATION", "ACTIVE", false, "NONE", "automation:$id")
        return PrivateLibraryItem(header, null, emptyList(), emptyList(), null, null, null, automation = content)
    }

    companion object {
        const val ROUTINE = "PRIVATE_ROUTINE"
        const val WATCH = "PRIVATE_CONTINUATION_WATCH"
        private const val maxRuns = 1000
    }
}
