package com.halo.app.everyday.collectors.android

import android.content.Context
import android.os.UserManager
import androidx.work.*
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** One bounded pass. Per-source cadence/backoff/quotas remain in the transactional coordinator. */
class CollectorRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!applicationContext.getSystemService(UserManager::class.java).isUserUnlocked) return Result.success()
        return try {
            val app = applicationContext as HaloApplication
            app.awaitOrganizerBootstrap()
            ManualDependencyGraph.collectorBootstrap.ensureInitialized()
            ManualDependencyGraph.collectorCoordinator.refreshDue(maximum = 5, external = false)
            app.awaitRemoteDispatchPolicy()
            ManualDependencyGraph.collectorCoordinator.refreshDue(maximum = 5, external = true)
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun maintain(context: Context) {
            if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("everyday-collectors-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CollectorRefreshWorker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
        }
        fun enqueue(context: Context) {
            if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
            WorkManager.getInstance(context).enqueueUniqueWork("everyday-collectors-refresh", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CollectorRefreshWorker>()
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
        }
    }
}
