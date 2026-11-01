package com.halo.app.everyday.organizer.android

import android.content.Context
import androidx.work.*
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class OrganizerScheduleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as HaloApplication).awaitOrganizerBootstrap()
        val owner = ManualDependencyGraph.organizerDelivery
        if (inputData.getBoolean("full", false)) owner.reconcileAll() else owner.reconcilePending()
        if (ManualDependencyGraph.organizerRepository.pendingScheduleIds().isEmpty()) Result.success() else Result.retry()
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (_: Exception) { Result.retry() }

    companion object {
        fun enqueue(context: Context, full: Boolean = false) {
            if (!context.getSystemService(android.os.UserManager::class.java).isUserUnlocked) return
            WorkManager.getInstance(context).enqueueUniqueWork("organizer-delivery-outbox", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<OrganizerScheduleWorker>().setInputData(workDataOf("full" to full))
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
        }
        fun maintain(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("organizer-delivery-recovery", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<OrganizerScheduleWorker>(6, TimeUnit.HOURS)
                    .setInputData(workDataOf("full" to true)).build())
        }
    }
}
