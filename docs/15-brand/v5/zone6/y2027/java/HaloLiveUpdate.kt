package com.halo.app.y2027

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/*
 * halo 2027 · Outside the app (board 48). One ongoing notification per running TaskPlan:
 *  - status-bar chip "Step 2/4" (Android 16+ promoted ongoing / Live Update),
 *  - progress cut into the plan's steps; ASK_FIRST steps get a point marker (Apricot),
 *  - lock screen shows a PUBLIC version: task title + step only, never message text or amounts.
 * Reference code. Verify the API 36 calls against the SDK you build with; older Android falls back to a plain progress bar.
 */
object HaloLiveUpdate {
    const val CHANNEL = "halo_tasks"
    private const val MINT = 0xFF0CF2BA.toInt()
    private const val APRICOT = 0xFFFF9F6B.toInt()

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "What halo is doing", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "One live line while halo works on something for you"
            setShowBadge(false)
        })
    }

    fun build(ctx: Context, plan: TaskPlan, open: PendingIntent, stop: PendingIntent): Notification {
        val title = if (plan.needsYou) "${plan.title} · waiting for you" else plan.title
        val text = plan.current?.liveLine ?: plan.current?.title ?: "Done"
        val chip = if (plan.needsYou) "Needs you" else plan.stepLabel().replace("Step ", "").replace(" of ", "/")

        val publicVersion = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(com.halo.app.R.drawable.ic_halo_brand_halo)
            .setContentTitle(plan.title).setContentText(plan.stepLabel())
            .build()

        if (Build.VERSION.SDK_INT >= 36) {
            val segments = plan.steps.map { Notification.ProgressStyle.Segment(100).setColor(MINT) }
            val points = plan.steps.withIndex().filter { it.value.effectiveKind == StepKind.ASK_FIRST }
                .map { Notification.ProgressStyle.Point(it.index * 100).setColor(APRICOT) }
            val style = Notification.ProgressStyle()
                .setProgressSegments(segments)
                .setProgressPoints(points)
                .setProgress(plan.doneCount * 100 + if (plan.current != null) 50 else 0)
            return Notification.Builder(ctx, CHANNEL)
                .setSmallIcon(com.halo.app.R.drawable.ic_halo_brand_halo)
                .setContentTitle(title).setContentText(text)
                .setStyle(style)
                .setOngoing(true)
                .setShortCriticalText(chip)                       // status-bar chip
                .setColor(if (plan.needsYou) APRICOT else MINT)
                .setContentIntent(open)
                .addAction(Notification.Action.Builder(null, "Stop", stop).build())
                .addAction(Notification.Action.Builder(null, "Open", open).build())
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(publicVersion)
                .apply { extras.putBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING, true) }
                .build()
        }
        return NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(com.halo.app.R.drawable.ic_halo_brand_halo)
            .setContentTitle(title).setContentText(text)
            .setProgress(plan.steps.size, plan.doneCount, false)
            .setOngoing(true).setOnlyAlertOnce(true)
            .setColor(if (plan.needsYou) APRICOT else MINT)
            .setContentIntent(open)
            .addAction(0, "Stop", stop).addAction(0, "Open", open)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .build()
    }
}
// Manifest: <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
//           <uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS"/>  (Android 16+)
