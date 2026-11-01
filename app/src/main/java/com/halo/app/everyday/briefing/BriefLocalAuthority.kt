package com.halo.app.everyday.briefing

import android.app.NotificationManager
import android.content.Context
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.collectors.SafeCollectorJson
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.TimelineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

/** Reuses the existing child project library and content owners; a local source flag never grants item access. */
class BriefLocalAuthority(private val context: Context, private val database: AppDatabase,
    private val storage: Storage, private val timeline: () -> List<TimelineEvent>,
    private val attentionIds: () -> Set<String>) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun changes() = callbackFlow {
        val observer = object : androidx.room.InvalidationTracker.Observer("workspace_objects", "agent_runs") {
            override fun onInvalidated(tables: Set<String>) { trySend(Unit) }
        }
        database.invalidationTracker.addObserver(observer)
        trySend(Unit)
        awaitClose { database.invalidationTracker.removeObserver(observer) }
    }
    suspend fun childFactIds(): Set<String> = withContext(Dispatchers.IO) {
        val raw = storage.getString("kids_project_library_v1") ?: return@withContext emptySet()
        val ids = try {
            val rows = SafeCollectorJson.objectValue("{\"items\":$raw}", 1024 * 1024).getJSONArray("items")
            require(rows.length() <= 200)
            (0 until rows.length()).map { rows.getJSONObject(it).getString("note") }.toSet()
        } catch (_: Exception) { return@withContext emptySet() }
        ids.filter { id -> database.notebookDao().getWorkspaceObject(id)?.let { it.type == "NOTE" && it.lifecycle == "ACTIVE" } == true }.toSet()
    }
    suspend fun stillExists(fact: BriefFact): Boolean = when (fact.kind) {
        BriefFactKind.NOTE -> fact.values["localId"]?.let { id -> database.notebookDao().getWorkspaceObject(id)?.lifecycle == "ACTIVE" } == true
        BriefFactKind.ACTIVITY -> fact.values["localId"]?.let { id -> when {
            id.startsWith("run:") -> database.agentRunDao().get(id.removePrefix("run:")) != null
            id.startsWith("timeline:") -> timeline().any { it.presentationId == id.removePrefix("timeline:") }
            else -> false
        } } == true
        BriefFactKind.NOTIFICATION -> context.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.packageName == context.packageName && it.key == fact.values["localId"]
        }
        BriefFactKind.ATTENTION -> fact.values["localId"] in attentionIds()
        else -> true
    }
}
