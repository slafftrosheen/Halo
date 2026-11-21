package com.halo.app.nativeactions

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A separate app-owned rule with both dream-end and durable crash recovery. */
internal object BedsideQuiet {
    private const val prefsName = "halo_bedside_quiet_v1"
    private const val owner = "dream"
    private const val failSafeMinutes = 8 * 60L

    fun start(context: Context, id: String, active: () -> Boolean) = synchronized(this) {
        if (!active()) return@synchronized
        val work = scheduleRelease(context, id, failSafeMinutes)
        work.result.get()
        if (!active()) return@synchronized
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        check(prefs.edit().putString(owner, id).commit())
        NativeDndController(context, bedside = true).request(true)
    }

    fun scheduleRelease(context: Context, id: String, minutes: Long): androidx.work.Operation {
        val request = OneTimeWorkRequestBuilder<BedsideQuietReleaseWorker>().setInputData(workDataOf(owner to id))
            .setInitialDelay(minutes, TimeUnit.MINUTES).build()
        return WorkManager.getInstance(context).enqueueUniqueWork("halo-bedside-release-$id-$minutes",
            ExistingWorkPolicy.KEEP, request)
    }

    fun release(context: Context, id: String) = synchronized(this) {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        if (prefs.getString(owner, null) != id) return@synchronized
        try { NativeDndController(context, bedside = true).request(false) }
        catch (_: SecurityException) {
            // Android removes or invalidates this app's rule authority when access is revoked.
        }
        check(prefs.edit().remove(owner).commit())
    }
}

class BedsideQuietReleaseWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("dream") ?: return Result.failure()
        return try {
            withContext(Dispatchers.IO) { BedsideQuiet.release(applicationContext, id) }
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
