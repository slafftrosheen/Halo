package com.halo.app.nativeactions

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.AtomicFile
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.halo.app.R
import com.halo.app.ui.mini.MiniHaloEntry
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Reminders receive no paper names, dates from documents, or Vault key. */
internal object HaloVaultReminder {
    private val lock = Any()
    private const val channel = "halo-vault-reminders-v1"

    fun authorized(context: Context): Boolean = context.getSystemService(NotificationManager::class.java)
        ?.areNotificationsEnabled() == true && (Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    fun apply(context: Context, paper: VaultPaper) = synchronized(lock) {
        val date = paper.remind
        if (date == null) { cancel(context, paper.id); return@synchronized }
        val manager = WorkManager.getInstance(context)
        val delay = Duration.between(Instant.now(), date.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant())
            .toMillis().coerceAtLeast(0L)
        val receipt = receipt(context, paper.id)
        check(receipt.baseFile.parentFile?.let { it.isDirectory || it.mkdirs() } == true)
        val marker = receipt.startWrite()
        try {
            marker.write(paper.revision.toString().toByteArray(Charsets.US_ASCII))
            marker.fd.sync()
            receipt.finishWrite(marker)
        } catch (error: Exception) { receipt.failWrite(marker); throw error }
        val work = OneTimeWorkRequestBuilder<HaloVaultReminderWorker>()
            .setInputData(workDataOf("id" to paper.id, "version" to paper.revision))
            .setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        manager.enqueueUniqueWork(key(paper.id), ExistingWorkPolicy.REPLACE, work).result.get()
    }

    fun cancel(context: Context, id: String) = synchronized(lock) {
        receipt(context, id).delete()
        WorkManager.getInstance(context).cancelUniqueWork(key(id))
        context.getSystemService(NotificationManager::class.java)?.cancel(id, 1)
    }

    fun notify(context: Context, id: String, revision: Long) = synchronized(lock) {
        val marker = receipt(context, id)
        val current = runCatching { marker.readFully().toString(Charsets.US_ASCII).toLong() }.getOrNull()
        if (current != revision || !authorized(context)) return@synchronized
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@synchronized
        manager.createNotificationChannel(NotificationChannel(channel, context.getString(R.string.halo_vault_title),
            NotificationManager.IMPORTANCE_DEFAULT).apply { lockscreenVisibility = Notification.VISIBILITY_SECRET })
        manager.notify(id, 1, NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_halo_brand_halo)
            .setContentTitle(context.getString(R.string.halo_vault_title))
            .setContentText(context.getString(R.string.halo_vault_reminder_alert)).setAutoCancel(true)
            .setContentIntent(MiniHaloEntry.pending(context, 8805, "vault"))
            .setVisibility(NotificationCompat.VISIBILITY_SECRET).build())
    }

    private fun key(id: String) = "halo-vault-reminder-" + UUID.fromString(id).toString()
    private fun receipt(context: Context, id: String) = AtomicFile(File(context.noBackupFilesDir,
        "halo-vault-v1/alarms/" + UUID.fromString(id).toString()))
}

class HaloVaultReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("id") ?: return Result.failure()
        val revision = inputData.getLong("version", 0)
        if (revision <= 0) return Result.failure()
        return try {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { HaloVaultReminder.notify(applicationContext, id, revision) }
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }
}
