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
    override fun onDeleted(context: Context, ids: IntArray) { ids.forEach { EverydayWidgetMotion.forget(it); EverydayWidgetStore(context).remove(it) }; EverydayWidgetUpdates.maintain(context) }
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
class HaloTodayWidget : EverydayWidgetProvider()

class HaloScanWidget : EverydayWidgetProvider()
class HaloRecordWidget : EverydayWidgetProvider()
class HaloQuickNoteWidget : EverydayWidgetProvider()
class HaloTranslateWidget : EverydayWidgetProvider()
class HaloAirPollenWidget : EverydayWidgetProvider()
class HaloPlannerWeekWidget : EverydayWidgetProvider()
class HaloFocusWidget : EverydayWidgetProvider()
class HaloLittleHabitsWidget : EverydayWidgetProvider()
class HaloNextRideWidget : EverydayWidgetProvider()
class HaloOurHomeWidget : EverydayWidgetProvider()
class HaloLearnWidget : EverydayWidgetProvider()
class HaloKidsSkyWidget : EverydayWidgetProvider()
class HaloYourThingsWidget : EverydayWidgetProvider()
class HaloLockNextEventWidget : EverydayWidgetProvider()
class HaloLockRainWidget : EverydayWidgetProvider()
class HaloSmartStackWidget : EverydayWidgetProvider()

object EverydayWidgetProviders {
    val kinds = linkedMapOf(HaloClockWidget::class.java to EverydayCardKind.CLOCK, HaloClockWeatherWidget::class.java to EverydayCardKind.CLOCK_WEATHER,
        HaloUpcomingWidget::class.java to EverydayCardKind.UPCOMING, HaloTasksWidget::class.java to EverydayCardKind.TASKS,
        HaloBriefWidget::class.java to EverydayCardKind.BRIEF, HaloHeadlinesWidget::class.java to EverydayCardKind.HEADLINES,
        HaloAlarmTimerWidget::class.java to EverydayCardKind.ALARMS_TIMERS, HaloDashboardWidget::class.java to EverydayCardKind.DASHBOARD,
        HaloTodayWidget::class.java to EverydayCardKind.TODAY,
        HaloScanWidget::class.java to EverydayCardKind.SCAN,
        HaloRecordWidget::class.java to EverydayCardKind.RECORD,
        HaloQuickNoteWidget::class.java to EverydayCardKind.QUICK_NOTE,
        HaloTranslateWidget::class.java to EverydayCardKind.TRANSLATE,
        HaloAirPollenWidget::class.java to EverydayCardKind.AIR_POLLEN,
        HaloPlannerWeekWidget::class.java to EverydayCardKind.PLANNER_WEEK,
        HaloFocusWidget::class.java to EverydayCardKind.FOCUS,
        HaloLittleHabitsWidget::class.java to EverydayCardKind.LITTLE_HABITS,
        HaloNextRideWidget::class.java to EverydayCardKind.NEXT_RIDE,
        HaloOurHomeWidget::class.java to EverydayCardKind.OUR_HOME,
        HaloLearnWidget::class.java to EverydayCardKind.LEARN,
        HaloKidsSkyWidget::class.java to EverydayCardKind.KIDS_SKY,
        HaloYourThingsWidget::class.java to EverydayCardKind.YOUR_THINGS,
        HaloLockNextEventWidget::class.java to EverydayCardKind.LOCK_NEXT_EVENT,
        HaloLockRainWidget::class.java to EverydayCardKind.LOCK_RAIN,
        HaloSmartStackWidget::class.java to EverydayCardKind.SMART_STACK)
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
            val pauseVersion = Graph.haloPause.state.value.version
            val snapshot = readEverydayWidgetSnapshot(context)
            val manager = AppWidgetManager.getInstance(context)
            val locale = java.util.Locale.forLanguageTag(Graph.appLanguagePreferences.language.value.tag)
            val rendered = ids.mapNotNull { id ->
                val kind = EverydayWidgetProviders.kind(context, id) ?: return@mapNotNull null
                val instance = EverydayWidgetStore(context).get(id) ?: EverydayWidgetInstance(id, 0, EverydayCardConfig(kind))
                require(instance.config.kind==kind)
                val displayConfig = instance.config.copy(limit = minOf(instance.config.limit, instance.config.widgetSize?.rowLimit ?: 3))
                val face = EverydayCards.project(displayConfig, snapshot, locale, externalHost = true, text = { context.nativeUi(it) })
                val frame = EverydayWidgetMotion.frame(instance, snapshot, face, locale.toLanguageTag())
                val transition = EverydayWidgetMotion.transition(instance, snapshot, frame)
                Triple(instance, EverydayWidgetViews.views(context, instance, snapshot,
                    manager.getAppWidgetOptions(id), transition = transition), frame)
            }
            Graph.appDatabase.withTransaction {
                if(readEverydayAuthorityStamp()!=snapshot.authorityStamp) throw WidgetPublicationChanged()
                if (Graph.haloPause.state.value.version != pauseVersion) throw WidgetPublicationChanged()
                rendered.forEach { (instance, views, frame) ->
                    currentCoroutineContext().ensureActive()
                    if(runCatching { Graph.kidsPreferences.requirePresentation(snapshot.familyRevision) }.isFailure) throw WidgetPublicationChanged()
                    if(Graph.privacyModes.revision!=snapshot.modeRevision) throw WidgetPublicationChanged()
                    EverydayWidgetStore(context).publishIfCurrent(instance,generation) {
                        if(EverydayWidgetProviders.kind(context,instance.id)==null) throw WidgetPublicationChanged()
                        manager.updateAppWidget(instance.id,views)
                        EverydayWidgetMotion.remember(instance.id, frame)
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
                  EverydayWidgetStore(context).publishIfCurrent(instance,generation) {
                      AppWidgetManager.getInstance(context).updateAppWidget(id, EverydayWidgetViews.unavailable(context,id))
                      EverydayWidgetMotion.forget(id)
                  }
              } }
              false
          }
    }
}

object EverydayWidgetUpdates {
    private const val PERIODIC = "halo-everyday-widget-periodic"
    private const val UPDATE = "halo-everyday-widget-update"
    private var observer: Job? = null
    private var stopWallpaperWatch: (() -> Unit)? = null
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val libraryBacked = setOf(EverydayCardKind.UPCOMING, EverydayCardKind.AGENDA, EverydayCardKind.TASKS,
        EverydayCardKind.REMINDERS, EverydayCardKind.ALARMS_TIMERS, EverydayCardKind.BRIEF, EverydayCardKind.DASHBOARD, EverydayCardKind.TODAY,
        EverydayCardKind.PLANNER_WEEK, EverydayCardKind.FOCUS, EverydayCardKind.LITTLE_HABITS, EverydayCardKind.OUR_HOME,
        EverydayCardKind.LEARN, EverydayCardKind.LOCK_NEXT_EVENT, EverydayCardKind.SMART_STACK)

    /** Remove already-published launcher snapshots before relying on a deferred repaint. */
    fun withdrawPrivateSnapshots(context: Context) {
        val generation = EverydayWidgetGeneration.invalidate()
        val manager = AppWidgetManager.getInstance(context)
        val store = EverydayWidgetStore(context)
        for (id in EverydayWidgetProviders.ids(context)) runCatching {
            val kind = EverydayWidgetProviders.kind(context, id) ?: return@runCatching
            if (kind !in libraryBacked) return@runCatching
            val instance = store.get(id) ?: EverydayWidgetInstance(id, 0, EverydayCardConfig(kind))
            store.publishIfCurrent(instance, generation) {
                manager.updateAppWidget(id, EverydayWidgetViews.unavailable(context, id))
                EverydayWidgetMotion.forget(id)
            }
        }
        enqueue(context)
    }

    fun enqueue(context: Context) {
        EverydayWidgetGeneration.invalidate()
        if (EverydayWidgetProviders.ids(context).isEmpty()) return
        WorkManager.getInstance(context).enqueueUniqueWork(UPDATE, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<EverydayWidgetWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
    }
    fun maintain(context: Context) {
        val manager = WorkManager.getInstance(context)
        if (EverydayWidgetProviders.ids(context).isEmpty()) {
            manager.cancelUniqueWork(PERIODIC); manager.cancelUniqueWork(UPDATE)
            observer?.cancel(); observer = null
            synchronized(this) { stopWallpaperWatch?.invoke(); stopWallpaperWatch = null }
        }
        else {
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<EverydayWidgetWorker>(30, TimeUnit.MINUTES).build())
            enqueue(context)
            observe(context,scope)
        }
    }
    @androidx.annotation.RequiresApi(27)
    private fun watchWallpaper(context: Context): () -> Unit {
        val wallpaper = android.app.WallpaperManager.getInstance(context)
        val listener = android.app.WallpaperManager.OnColorsChangedListener { _, which ->
            if (which and android.app.WallpaperManager.FLAG_SYSTEM != 0) enqueue(context)
        }
        wallpaper.addOnColorsChangedListener(listener, android.os.Handler(android.os.Looper.getMainLooper()))
        return { wallpaper.removeOnColorsChangedListener(listener) }
    }
    @OptIn(FlowPreview::class)
    @Synchronized fun observe(context: Context, scope: CoroutineScope) {
        if (observer?.isActive == true) return
        if (android.os.Build.VERSION.SDK_INT >= 27 && stopWallpaperWatch == null) {
            stopWallpaperWatch = runCatching { watchWallpaper(context.applicationContext) }.getOrNull()
        }
        observer = scope.launch {
            combine(Graph.organizerRepository.observe(),Graph.organizerRepository.observeReceipts(), Graph.collectorRepository.observe(), Graph.briefRepository.observe(),Graph.briefRepository.observeSettings(),
                Graph.worldClocks.observe(), Graph.kidsPreferences.enabled, Graph.privacyModes.mode,
                Graph.appLanguagePreferences.language,Graph.assistantPreferences.state,Graph.articlePictureSettings.visible,
                Graph.studyRepository.observeActiveSessions(), Graph.studyLessonStore.widgetJourneyRevision,
                com.halo.app.ui.toolkit.HaloFocusOwner.get(context).state, com.halo.app.ui.toolkit.HaloLifeOwner.get(context).family,
                Graph.routineRepository.observeRecentRuns(), Graph.routineRepository.observeRoutinesReadOnly(),
                EverydayWidgetRecording.active,
                Graph.studyRepository.observeChildAttemptCount(0L, Long.MAX_VALUE)) { _: Array<Any?> -> Unit }.debounce(500).collect { enqueue(context) }
        }
    }
}

class EverydayWidgetMaintenanceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { if (intent.action in setOf(Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_DATE_CHANGED, Intent.ACTION_LOCALE_CHANGED,
        Intent.ACTION_USER_UNLOCKED)) EverydayWidgetUpdates.maintain(context) }
}
