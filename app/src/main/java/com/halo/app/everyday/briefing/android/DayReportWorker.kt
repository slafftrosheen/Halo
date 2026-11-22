package com.halo.app.everyday.briefing.android

import android.content.Context
import android.os.UserManager
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph as Graph
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

/** Prepares an on-device report after 21:00 local time. Android controls when background work runs. */
class DayReportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        if (applicationContext.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return Result.success()
        return try {
            (applicationContext as HaloApplication).awaitOrganizerBootstrap()
            Graph.collectorBootstrap.ensureInitialized()
            Graph.kidsPreferences.getEnabled()
            if (java.time.LocalTime.now().hour >= 21) Graph.dayReportService.prepareEvening()
            Graph.todayNews.refreshIfDue()
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: Exception) { Result.retry() }
    }

    companion object {
        fun maintain(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("halo-your-day-evening", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<DayReportWorker>(15, TimeUnit.MINUTES).build())
        }
    }
}
