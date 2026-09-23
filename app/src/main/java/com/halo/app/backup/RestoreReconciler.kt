package com.halo.app.backup

import android.content.Context
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.WorkspaceObjectId
import com.halo.app.data.notebook.WorkspaceObjectType
import com.halo.app.data.search.AppSearchWorkspaceSearchEngine
import com.halo.app.data.settings.HaloSettingsRepository
import com.halo.app.data.workspace.RoomWorkspaceFileRepository
import com.halo.app.storage.haloStorageLayout
import com.halo.host.android.reminder.ReminderRecordStore
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** Idempotent local repair after portable import or Android's device restore. */
class RestoreReconciler(
    private val context: Context,
    private val database: AppDatabase,
    private val settings: HaloSettingsRepository,
) {
    suspend fun reconcile() = withContext(Dispatchers.IO) {
        RoomWorkspaceFileRepository(context, database, haloStorageLayout(context).managedWorkspaceFiles).reconcileAvailability()
        ReminderRecordStore(context).reconcile()
        val search = AppSearchWorkspaceSearchEngine(context, database.notebookDao())
        runCatching { search.clearDerivedIndex() }
        val bodies = database.brainSnapshotDao().notes().associateBy { it.objectId }
        database.brainSnapshotDao().workspaceObjects().forEach { item ->
            val type = runCatching { WorkspaceObjectType.valueOf(item.type) }.getOrNull() ?: return@forEach
            search.indexObject(WorkspaceObjectId(item.id), type, item.title, bodies[item.id]?.body.orEmpty(), item.updatedAt)
        }
        val generation = settings.settings.first().restoreGeneration
        settings.setRestoreGeneration(generation + 1)
    }

    suspend fun reconcileAfterSystemRestore() = withContext(Dispatchers.IO) {
        val marker = File(context.noBackupFilesDir, "halo.install.marker")
        if (!marker.exists()) {
            if (database.brainSnapshotDao().workspaceObjects().isNotEmpty()) reconcile()
            marker.parentFile?.mkdirs()
            marker.writeText("installed")
        }
    }
}
