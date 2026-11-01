package com.halo.app.everyday.collectors.android

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.halo.app.data.AppDatabase
import com.halo.app.everyday.collectors.*
import com.halo.app.everyday.organizer.*
import com.halo.sdk.runtime.TimelineEvent
import java.time.Clock
import java.time.Instant

/** Passive adapters: no runtime/model startup, no notification-listener or location permission request. */
class AndroidLocalCollectors(private val context: Context, private val database: AppDatabase,
    private val organizer: OrganizerRepository, private val timeline: () -> List<TimelineEvent>,
    private val runtimeStarted: () -> Boolean, private val modelFacts: () -> List<LocalFact>,
    private val clock: Clock = Clock.systemUTC()) {
    fun adapters(): List<Collector> = listOf(
        LocalStateCollector(CollectorKind.LOCAL_ORGANIZER) {
            organizer.records().filter { it.status == OrganizerStatus.ACTIVE }
                .sortedWith(compareByDescending<OrganizerRecord> { it.priority }.thenByDescending { it.updatedAt }.thenBy { it.id })
                .take(100).map { record -> LocalFact(record.id, record.kind.name, record.title, record.details.take(500),
                    "halo://organizer/record/${record.id}", record.version.toString(), record.updatedAt) }
        },
        LocalStateCollector(CollectorKind.LOCAL_WORKSPACE) {
            database.notebookDao().recentNoteObjects(100).map { note -> LocalFact(note.id, "NOTE", note.title, "",
                "halo://workspace/object/${note.id}", note.updatedAt.toString(), Instant.ofEpochMilli(note.updatedAt)) }
        },
        LocalStateCollector(CollectorKind.LOCAL_ACTIVITY) {
            val runs = database.agentRunDao().recentRoots(60).map { run -> LocalFact("run:${run.id}", "ACTIVITY",
                run.request.take(200), run.state, "halo://activity/run/${run.id}", "${run.updatedAt}:${run.state}", Instant.ofEpochMilli(run.updatedAt)) }
            val events = timeline().takeLast(40).map { event -> LocalFact("timeline:${event.presentationId}", "TIMELINE",
                event.title.take(200), event.description.take(500), "halo://activity/timeline/${android.net.Uri.encode(event.presentationId)}",
                "${event.timestamp}:${event.status}", event.timestamp) }
            (runs + events).sortedWith(compareByDescending<LocalFact> { it.occurredAt }.thenBy { it.id })
        },
        LocalStateCollector(CollectorKind.LOCAL_NOTIFICATIONS) {
            // Android returns notifications owned by this package; no other-app listener data is read.
            context.getSystemService(NotificationManager::class.java).activeNotifications
                .filter { it.packageName == context.packageName }.sortedByDescending { it.postTime }.take(100).map { item ->
                    val title = item.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().take(200)
                    val body = item.notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty().take(500)
                    LocalFact(item.key, "HALO_NOTIFICATION", title.ifBlank { "Halo" }, body, "halo://activity",
                        "${item.postTime}:${item.id}", Instant.ofEpochMilli(item.postTime))
                }
        },
        LocalStateCollector(CollectorKind.LOCAL_DEVICE) {
            buildList {
                val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                if (level >= 0 && scale > 0) {
                    val percent = (100L * level / scale).coerceIn(0, 100)
                    val charging = battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) in setOf(BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL)
                    add(LocalFact("device:battery", "BATTERY", "$percent%", if (charging) "CHARGING" else "ON_BATTERY",
                        "halo://device", "$percent:$charging", clock.instant()))
                }
                val started = runtimeStarted()
                add(LocalFact("device:runtime", "RUNTIME", "Halo", if (started) "RUNNING" else "IDLE", "halo://device", started.toString(), null))
                addAll(modelFacts())
            }
        },
    )
}
