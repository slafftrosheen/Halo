package com.halo.app.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph
import kotlinx.coroutines.*

class AutomationReconcileReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                (context.applicationContext as HaloApplication).awaitAutomationBootstrap()
                com.halo.host.android.reminder.ReminderRecordStore(context).reconcile()
                // WorkManager owns reboot persistence. Only wall-clock changes replace time work here.
                if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                    ManualDependencyGraph.routineRepository.all().filter { it.enabled }.forEach {
                        ManualDependencyGraph.routineScheduler.schedule(it)
                    }
                }
            } catch (e: CancellationException) { throw e }
              catch (e: Exception) { android.util.Log.e("AutomationRecovery", "Reconciliation failed", e) }
              finally { result.finish() }
        }
    }
}
