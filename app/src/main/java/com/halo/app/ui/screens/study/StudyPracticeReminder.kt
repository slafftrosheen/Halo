package com.halo.app.ui.screens.study

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.halo.app.HaloApplication
import com.halo.app.MainActivity
import com.halo.app.R
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.ui.shell.HaloScreenCatalog
import kotlinx.coroutines.CancellationException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Approximate local-evening reminder. Reads due counts only and never brings lesson content to the lock screen. */
class StudyPracticeReminder(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return Result.retry()
        return try {
            (context as HaloApplication).awaitStorageBootstrap()
            if (!StudyPresentation.load(Graph.storage).practiceReminder) return Result.success()
            val now = ZonedDateTime.now()
            val today = now.toLocalDate()
            val expected = inputData.getString(DUE_DAY)?.let(LocalDate::parse)
            val inWindow = !now.toLocalTime().isBefore(EVENING) && now.toLocalTime().isBefore(NIGHT)
            if (expected != null && !expected.isAfter(today) && inWindow && !Graph.kidsPreferences.getEnabled() &&
                Graph.storage.getString(LAST_SHOWN) != today.toString() &&
                Graph.appDatabase.studyDao().dueCount(now.toInstant().toEpochMilli()) > 0 &&
                StudyPresentation.load(Graph.storage).practiceReminder && notifyIfPermitted(context)
            ) {
                Graph.storage.putString(LAST_SHOWN, today.toString())
            }
            val nextDay = if (now.toLocalTime().isBefore(EVENING)) today else today.plusDays(1)
            // When local time changes, an early execution replaces the old plan with today's corrected time.
            if (StudyPresentation.load(Graph.storage).practiceReminder)
                schedule(context, now, nextDay, replace = expected == nextDay)
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun notifyIfPermitted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        if (!manager.areNotificationsEnabled()) return false
        val title = context.getString(R.string.coach_practice_reminder)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, title, NotificationManager.IMPORTANCE_DEFAULT))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(HaloScreenCatalog.EXTRA_SCREEN, "study"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(NOTIFICATION_ID, Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.halo_ic_study)
            .setContentTitle(context.getString(R.string.coach_practice_due))
            .setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(open)
            .build())
        return true
    }

    companion object {
        private const val TAG = "halo.study.practice.reminder"
        private const val DUE_DAY = "study.reminder.day"
        private const val LAST_SHOWN = "study.practice.last_reminder"
        private const val CHANNEL = "halo.study.practice"
        private const val NOTIFICATION_ID = 0x4818
        private val EVENING = LocalTime.of(18, 0)
        private val NIGHT = LocalTime.of(22, 0)

        fun maintain(context: Context, enabled: Boolean) {
            if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return
            if (!enabled) {
                WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
                context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
                return
            }
            val now = ZonedDateTime.now()
            val day = if (now.toLocalTime().isBefore(EVENING)) now.toLocalDate() else now.toLocalDate().plusDays(1)
            schedule(context, now, day, replace = false)
        }

        private fun schedule(context: Context, now: ZonedDateTime, day: LocalDate, replace: Boolean) {
            val at = day.atTime(EVENING).atZone(now.zone)
            val delay = Duration.between(now, at).toMillis().coerceAtLeast(0)
            val request = OneTimeWorkRequestBuilder<StudyPracticeReminder>()
                .setInputData(androidx.work.workDataOf(DUE_DAY to day.toString()))
                .setInitialDelay(delay, TimeUnit.MILLISECONDS).addTag(TAG).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "$TAG:$day", if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request)
        }
    }
}
