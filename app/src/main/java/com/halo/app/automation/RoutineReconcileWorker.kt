package com.halo.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph

class RoutineReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        WorkManager.getInstance(context).enqueueUniqueWork("halo.routines.reconcile", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<RoutineReconcileWorker>().setInputData(workDataOf("clockChanged" to
                (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED))).build())
    }
}
class RoutineReconcileWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as HaloApplication).awaitAutomationBootstrap()
        ManualDependencyGraph.routineScheduler.reconcile(inputData.getBoolean("clockChanged", false))
        return Result.success()
    }
}
