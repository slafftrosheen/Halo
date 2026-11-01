package com.halo.app.everyday.organizer.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.*

class OrganizerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                val unlocked = context.getSystemService(UserManager::class.java).isUserUnlocked
                if (intent.action !in setOf(OrganizerIntents.FIRE, OrganizerIntents.STOP, OrganizerIntents.SNOOZE)) {
                    if (unlocked) OrganizerScheduleWorker.enqueue(context, full = true) else OrganizerLockedDelivery(context).reconcile()
                    return@launch
                }
                val key = intent.getStringExtra(OrganizerIntents.KEY)?.takeIf { it.length <= 500 && '@' in it } ?: return@launch
                val version = intent.getLongExtra(OrganizerIntents.VERSION, -1).takeIf { it > 0 } ?: return@launch
                val trigger = intent.getLongExtra(OrganizerIntents.TRIGGER, -1).takeIf { it >= 0 }?.let(Instant::ofEpochMilli) ?: return@launch
                if (!unlocked) {
                    val owner = OrganizerLockedDelivery(context)
                    if (intent.action == OrganizerIntents.FIRE) owner.fire(key, version, trigger)
                    else owner.action(key, version, trigger, intent.action == OrganizerIntents.SNOOZE)
                } else {
                    (context.applicationContext as HaloApplication).awaitOrganizerBootstrap()
                    val owner = ManualDependencyGraph.organizerDelivery
                    when (intent.action) {
                        OrganizerIntents.FIRE -> owner.fire(key, version, trigger)
                        OrganizerIntents.STOP -> owner.dismiss(key, version, trigger)
                        OrganizerIntents.SNOOZE -> owner.snooze(key, version, Duration.ofMinutes(5), trigger)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (error: Exception) { android.util.Log.w("OrganizerDelivery", "Delivery could not complete; durable state retained", error) }
              finally { pending.finish() }
        }
    }
    private companion object { val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
