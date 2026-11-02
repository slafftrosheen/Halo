package com.halo.app.nativeactions

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.*
import android.os.Bundle
import androidx.work.*
import androidx.room.withTransaction
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.presentation.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

abstract class EverydayWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = EverydayWidgetUpdates.enqueue(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) = EverydayWidgetUpdates.enqueue(context)
    override fun onEnabled(context: Context) = EverydayWidgetUpdates.maintain(context)
    override fun onDisabled(context: Context) = EverydayWidgetUpdates.maintain(context)
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { EverydayWidgetStore(context).remove(it) }; EverydayWidgetUpdates.maintain(context) }
    override fun onRestored(context: Context, oldIds: IntArray, newIds: IntArray) {
        oldIds.zip(newIds).forEach { (old,new) -> EverydayWidgetStore(context).restored(old,new) }
        EverydayWidgetUpdates.enqueue(context)
    }
}
class HaloClockWidget : EverydayWidgetProvider()
class HaloClockWeatherWidget : EverydayWidgetProvider()
class HaloUpcomingWidget : EverydayWidgetProvider()
class HaloTasksWidget : EverydayWidgetProvider()
class HaloBriefWidget : EverydayWidgetProvider()
class HaloHeadlinesWidget : EverydayWidgetProvider()
class HaloAlarmTimerWidget : EverydayWidgetProvider()
class HaloDashboardWidget : EverydayWidgetProvider()

object EverydayWidgetProviders {
    val kinds = linkedMapOf(HaloClockWidget::class.java to EverydayCardKind.CLOCK, HaloClockWeatherWidget::class.java to EverydayCardKind.CLOCK_WEATHER,
        HaloUpcomingWidget::class.java to EverydayCardKind.UPCOMING, HaloTasksWidget::class.java to EverydayCardKind.TASKS,
        HaloBriefWidget::class.java to EverydayCardKind.BRIEF, HaloHeadlinesWidget::class.java to EverydayCardKind.HEADLINES,
        HaloAlarmTimerWidget::class.java to EverydayCardKind.ALARMS_TIMERS, HaloDashboardWidget::class.java to EverydayCardKind.DASHBOARD)
    fun ids(context: Context): List<Int> = AppWidgetManager.getInstance(context).let { manager ->
        kinds.keys.flatMap { manager.getAppWidgetIds(ComponentName(context, it)).toList() }
    }
    fun kind(context: Context, id: Int): EverydayCardKind? {
        val provider = AppWidgetManager.getInstance(context).getAppWidgetInfo(id)?.provider ?: return null
        return kinds.entries.find { ComponentName(context, it.key) == provider }?.value
    }
}

/** OS-bounded cached repaint. No external fetch, every-second job, foreground service or wake lock. */
class EverydayWidgetWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = if (EverydayWidgetPainter.paint(applicationContext,EverydayWidgetProviders.ids(applicationContext))) Result.success() else Result.retry()
}

/** The configuration activity and background jobs publish through the same versioned owner. */
object EverydayWidgetPainter {
    suspend fun paint(context: Context,ids: List<Int>): Boolean {
        if (ids.isEmpty()) return true
        if (context.getSystemService(android.os.UserManager::class.java)?.isUserUnlocked != true) return false
        val generation=EverydayWidgetGeneration.invalidate()
        return try {
            Graph.init(context)
            (context.applicationContext as? HaloApplication)?.awaitOrganizerBootstrap()
            val snapshot = readEverydaySnapshot(context)
            val manager = AppWidgetManager.getInstance(context)
            val rendered=ids.mapNotNull { id ->
                val kind = EverydayWidgetProviders.kind(context, id) ?: return@mapNotNull null
                val instance = EverydayWidgetStore(context).get(id) ?: EverydayWidgetInstance(id, 0, EverydayCardConfig(kind))
                require(instance.config.kind==kind)
                instance to EverydayWidgetViews.views(context,instance,snapshot,manager.getAppWidgetOptions(id))
            }
            Graph.appDatabase.withTransaction {
                if(readEverydayAuthorityStamp()!=snapshot.authorityStamp) throw WidgetPublicationChanged()
                rendered.forEach { (instance,views) ->
                    currentCoroutineContext().ensureActive()
                    if(runCatching { Graph.kidsPreferences.requirePresentation(snapshot.familyRevision) }.isFailure) throw WidgetPublicationChanged()
                    if(Graph.privacyModes.revision!=snapshot.modeRevision) throw WidgetPublicationChanged()
                    EverydayWidgetStore(context).publishIfCurrent(instance,generation) {
                        if(EverydayWidgetProviders.kind(context,instance.id)==null) throw WidgetPublicationChanged()
                        manager.updateAppWidget(instance.id,views)
                    }
                }
            }
            true
        } catch (cancelled: CancellationException) { throw cancelled }
          catch (_: WidgetPublicationChanged) { false }
          catch (_: Exception) {
              ids.forEach { id -> runCatching {
                  val kind=EverydayWidgetProviders.kind(context,id) ?: return@runCatching
                  val instance=EverydayWidgetStore(context).get(id) ?: EverydayWidgetInstance(id,0,EverydayCardConfig(kind))
                  EverydayWidgetStore(context).publishIfCurrent(instance,generation) { AppWidgetManager.getInstance(context).updateAppWidget(id, EverydayWidgetViews.unavailable(context,id)) }
              } }
              false
          }
    }
}

object EverydayWidgetUpdates {
    private const val PERIODIC = "halo-everyday-widget-periodic"
    private const val UPDATE = "halo-everyday-widget-update"
    private var observer: Job? = null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    fun enqueue(context: Context) {
        EverydayWidgetGeneration.invalidate()
        if (EverydayWidgetProviders.ids(context).isEmpty()) return
        WorkManager.getInstance(context).enqueueUniqueWork(UPDATE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<EverydayWidgetWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun maintain(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (EverydayWidgetProviders.ids(context).isEmpty()) { manager.cancelUniqueWork(PERIODIC); manager.cancelUniqueWork(UPDATE);observer?.cancel();observer=null }
        else {
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<EverydayWidgetWorker>(30, TimeUnit.MINUTES).build())
            enqueue(context)
            observe(context,scope)
        }
    }
    @OptIn(FlowPreview::class)
    @Synchronized fun observe(context: Context, scope: CoroutineScope) {
        if (observer?.isActive == true) return
        observer = scope.launch {
            combine(Graph.organizerRepository.observe(),Graph.organizerRepository.observeReceipts(), Graph.collectorRepository.observe(), Graph.briefRepository.observe(),Graph.briefRepository.observeSettings(),
                Graph.worldClocks.observe(), Graph.kidsPreferences.enabled, Graph.privacyModes.mode,
                Graph.appLanguagePreferences.language,Graph.assistantPreferences.state) { _: Array<Any?> -> Unit }.debounce(500).collect { enqueue(context) }
        }
    }
}

class EverydayWidgetMaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
        Intent.ACTION_USER_UNLOCKED)) EverydayWidgetUpdates.maintain(context) }
}
