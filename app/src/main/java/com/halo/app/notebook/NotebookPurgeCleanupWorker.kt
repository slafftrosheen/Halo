package com.halo.app.notebook

import android.content.Context
import androidx.work.*
import com.halo.app.data.AppDatabase
import com.halo.app.data.notebook.NotebookPurgeOwner
import com.halo.app.storage.haloStorageLayout
import kotlinx.coroutines.CancellationException

/** Resumes only durable, already approved Notebook deletion receipts; no runtime/providers. */
class NotebookPurgeCleanupWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val database = AppDatabase.create(applicationContext)
        return try {
            val pending = NotebookPurgeOwner(database, haloStorageLayout(applicationContext).managedWorkspaceFiles).reconcile()
            if (pending) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
        finally { database.close() }
    }
    companion object {
        fun enqueue(context: Context) {
            // Append preserves a request arriving as the previous worker finishes its final query.
            WorkManager.getInstance(context).enqueueUniqueWork("halo.notebook.purge-cleanup", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<NotebookPurgeCleanupWorker>().build())
        }
    }
}
