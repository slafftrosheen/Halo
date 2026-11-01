package com.halo.app.everyday.briefing.android

import android.content.Context
import android.os.UserManager
import androidx.work.*
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph as Graph
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/** Opt-in, approximate scheduling. No exact alarm, wake lock, network refresh or automatic reading. */
class BriefWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (!applicationContext.getSystemService(UserManager::class.java).isUserUnlocked) return Result.success()
        return try {
            (applicationContext as HaloApplication).awaitOrganizerBootstrap()
            Graph.kidsPreferences.getEnabled()
            Graph.collectorBootstrap.ensureInitialized()
            Graph.briefProactive.runDue()
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { Result.retry() }
    }
    companion object {
        fun maintain(context: Context, enabled: Boolean) {
            if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return
            val manager = WorkManager.getInstance(context)
            if (!enabled) { manager.cancelUniqueWork("everyday-briefs-periodic"); return }
            manager.enqueueUniquePeriodicWork("everyday-briefs-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<BriefWorker>(15, TimeUnit.MINUTES)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build())
        }
    }
}
