package com.halo.app.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.halo.app.MainActivity
import com.halo.app.R
import com.halo.app.ai.PrivacyMode
import com.halo.app.di.ManualDependencyGraph
import com.halo.app.ui.shell.HaloScreenCatalog
import com.halo.runtime.cognition.agent.AgentRun
import com.halo.runtime.cognition.agent.AgentRunState
import com.halo.runtime.cognition.agent.AgentStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

/** One Android notification projected from observed run steps. No prompt, tool arguments or answer leave the app. */
internal class HaloPlanLiveUpdates private constructor(private val context: Context) {
    private val storage = context.getSharedPreferences("halo_live_task_v1", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(storage.getBoolean(KEY, false))
    val enabled = mutable.asStateFlow()
    suspend fun setEnabled(value: Boolean) = withContext(Dispatchers.IO) {
        check(storage.edit().putBoolean(KEY, value).commit())
        mutable.value = value
    }
    private val notifications get() = requireNotNull(context.getSystemService(NotificationManager::class.java))

    suspend fun observe() {
        val graph = ManualDependencyGraph
        combine(enabled, graph.kidsPreferences.loadedState, graph.kidsPreferences.enabled,
            graph.privacyModes.mode, graph.agentRunStore.observeRuns()) { enabled, loaded, kids, mode, runs ->
            if (enabled && loaded && !kids && mode == PrivacyMode.CLOUD) select(runs) else null
        }.combine(graph.haloPause.state) { run, pause -> run.takeUnless { pause.paused } }.collect { run ->
            if (run == null || !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                notifications.cancel(NOTIFICATION_ID)
            } else {
                try { show(run) } catch (_: SecurityException) { notifications.cancel(NOTIFICATION_ID) }
            }
        }
    }

    private fun select(runs: List<AgentRun>): AgentRun? = runs.asSequence().filter { run ->
        run.parentRunId == null && run.startedAt != null &&
            run.state in setOf(AgentRunState.PLANNING, AgentRunState.EXECUTING,
                AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION) &&
            run.steps.any { step -> step is AgentStep.PlanCreated && step.steps.size > 1 }
    }.maxByOrNull { it.updatedAt }

    private fun show(run: AgentRun) {
        val requested = run.steps.filterIsInstance<AgentStep.ToolInvocationPrepared>()
            .filter { it.toolId != "halo_workflow_plan" }.map { it.toolCallId }.distinct()
        val completed = run.steps.mapNotNull { step ->
            when (step) {
                is AgentStep.ToolOutcomeRecorded -> step.toolCallId
                is AgentStep.ToolCompleted -> step.toolCallId
                is AgentStep.ToolFailed -> step.toolCallId
                else -> null
            }
        }.toSet().intersect(requested.toSet()).size
        val review = run.state in setOf(AgentRunState.WAITING_FOR_APPROVAL, AgentRunState.WAITING_FOR_CLARIFICATION)
        val total = requested.size.coerceIn(0, 24)
        val done = completed.coerceIn(0, total)
        val title = context.getString(if (review) R.string.halo_plan_waiting else R.string.halo_plan_running)
        val count = if (total == 0) context.getString(R.string.halo_plan_preparing)
            else context.getString(R.string.halo_plan_actions, done, total)
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.halo_plan_channel),
            NotificationManager.IMPORTANCE_DEFAULT).apply { setShowBadge(false); setSound(null, null) })
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).putExtra(HaloScreenCatalog.EXTRA_SCREEN, "activity"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.halo_ic_timer)
            .setContentTitle(title).setContentText(count).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).setVisibility(Notification.VISIBILITY_PUBLIC)
        val color = if (review) 0xFFFFB98A.toInt() else 0xFF0CF2BA.toInt()
        builder.setColor(color)
        if (Build.VERSION.SDK_INT >= 36) {
            val style = Notification.ProgressStyle()
            if (total == 0) style.setProgressIndeterminate(true)
            else {
                style.setProgressSegments(List(total) { Notification.ProgressStyle.Segment(100).setColor(0xFF0CF2BA.toInt()) })
                if (review) style.addProgressPoint(Notification.ProgressStyle.Point(done * 100).setColor(color))
                style.setProgress(done * 100)
            }
            builder.setStyle(style).setRequestPromotedOngoing(true).setShortCriticalText(
                if (review || total == 0) context.getString(if (review) R.string.halo_plan_short_wait else R.string.halo_plan_short_work)
                else "$done/$total")
        } else if (total == 0) builder.setProgress(0, 0, true) else builder.setProgress(total, done, false)
        notifications.notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        private const val KEY = "on"
        private const val CHANNEL = "halo_live_plan"
        private const val NOTIFICATION_ID = 84512
        @Volatile private var instance: HaloPlanLiveUpdates? = null
        fun get(context: Context): HaloPlanLiveUpdates = instance ?: synchronized(this) {
            instance ?: HaloPlanLiveUpdates(context.applicationContext).also { instance = it }
        }
    }
}
