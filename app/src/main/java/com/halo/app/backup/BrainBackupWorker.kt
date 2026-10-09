package com.halo.app.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph
import java.util.concurrent.TimeUnit

class BrainBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as? HaloApplication)?.awaitStorageBootstrap()
        ManualDependencyGraph.vaultRepository.createBackup(
            BrainExportRequest(profile = BrainExportProfile.FULL_USER_BACKUP)
        )
        Result.success()
    } catch (_: SecurityException) {
        Result.failure()
    } catch (_: IllegalStateException) {
        Result.failure()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        // WorkManager needs the cancellation, not a synthetic retry.
        throw cancelled
    } catch (error: Exception) {
        android.util.Log.w("HaloBackup", "Backup attempt failed: ${error.javaClass.simpleName}")
        if (runAttemptCount >= 3) Result.failure() else Result.retry()
    }
}

enum class AutomaticBackupSchedule { OFF, DAILY, WEEKLY }

object BrainBackupScheduler {
    private const val UNIQUE_WORK = "halo.brain.backup"

    fun schedule(context: Context, schedule: AutomaticBackupSchedule) {
        val manager = WorkManager.getInstance(context)
        if (schedule == AutomaticBackupSchedule.OFF) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val hours = if (schedule == AutomaticBackupSchedule.DAILY) 24L else 7L * 24L
        val request = PeriodicWorkRequestBuilder<BrainBackupWorker>(hours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .setRequiresStorageNotLow(true)
                    .build()
            )
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
