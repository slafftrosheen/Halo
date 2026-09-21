package com.halo.app.agent

import android.content.Context
import androidx.work.Constraints
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

data class AgentWorkSpec(
    val id: String,
    val taskType: String,
    val initialDelayMs: Long = 0L,
    val requiresCharging: Boolean = false,
    val requiresStorageNotLow: Boolean = false
)

interface AgentWorkScheduler {
    suspend fun schedule(spec: AgentWorkSpec): String
    suspend fun cancel(id: String)
}

class WorkManagerAgentWorkScheduler(
    private val context: Context
) : AgentWorkScheduler {

    override suspend fun schedule(spec: AgentWorkSpec): String {
        val constraints = Constraints.Builder()
            .setRequiresCharging(spec.requiresCharging)
            .setRequiresStorageNotLow(spec.requiresStorageNotLow)
            .build()

        val request = OneTimeWorkRequestBuilder<BackgroundAgentWorker>()
            .setConstraints(constraints)
            .setInitialDelay(spec.initialDelayMs, TimeUnit.MILLISECONDS)
            .addTag(spec.id)
            .build()

        WorkManager.getInstance(context).enqueue(request)
        return spec.id
    }

    override suspend fun cancel(id: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag(id)
    }
}

class BackgroundAgentWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        // Perform background indexing / maintenance work
        return Result.success()
    }
}
