package com.halo.app.nativeactions

import android.app.KeyguardManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.presentation.EverydayCardConfig
import com.halo.app.everyday.presentation.EverydayCardKind
import com.halo.app.everyday.presentation.EverydaySnapshot
import com.halo.app.everyday.presentation.EverydayToolkitWidgets
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

internal object EverydayWidgetPin {
    internal const val REQUEST = "com.halo.app.action.WIDGET_PIN_CONFIRMED"
    internal const val DRAFT = "com.halo.app.extra.WIDGET_PIN_DRAFT"
    internal const val EXISTING_IDS = "com.halo.app.extra.WIDGET_PIN_EXISTING_IDS"
    internal const val CREATED_AT = "com.halo.app.extra.WIDGET_PIN_CREATED_AT"
    internal const val ADULT_REVISION = "com.halo.app.extra.WIDGET_PIN_ADULT_REVISION"
    internal const val PAUSE_VERSION = "com.halo.app.extra.WIDGET_PIN_PAUSE_VERSION"
    internal const val MAX_AGE = 5 * 60_000L

    fun request(context: Context, config: EverydayCardConfig, snapshot: EverydaySnapshot): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) return false
        val provider = EverydayWidgetProviders.kinds.entries.single { it.value == config.kind }.key
        val component = ComponentName(context, provider)
        com.halo.app.ui.theme.HaloLayoutLock.get(context).requireUnlocked()
        val revision = Graph.kidsPreferences.adultActionRevision()
        Graph.kidsPreferences.requirePresentation(snapshot.familyRevision)
        val pauseVersion = Graph.haloPause.state.value.version
        if (config.adaptiveDashboard) {
            check(!snapshot.kids && context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false)
            Graph.haloPause.requireActive()
        }
        val callback = PendingIntent.getBroadcast(context, 0,
            Intent(context, EverydayWidgetPinReceiver::class.java).setAction(REQUEST)
                .setData(Uri.Builder().scheme("halo").authority("widget-pin").appendPath(UUID.randomUUID().toString()).build())
                .putExtra(DRAFT, JSONObject(config.fields()).toString())
                .putExtra(EXISTING_IDS, manager.getAppWidgetIds(component))
                .putExtra(CREATED_AT, System.currentTimeMillis())
                .putExtra(ADULT_REVISION, revision)
                .putExtra(PAUSE_VERSION, pauseVersion),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
        val size = config.widgetSize ?: EverydayToolkitWidgets.defaultSize(config.kind)
        val preview = EverydayWidgetViews.render(context, EverydayWidgetInstance(0, 0, config), snapshot,
            size.widthDp, EverydayToolkitWidgets.previewHeight(config), preview = true)
        val extras = Bundle().apply { putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview) }
        return try {
            Graph.kidsPreferences.requireAdultAction(revision)
            if (config.adaptiveDashboard) {
                Graph.haloPause.requireActive()
                check(Graph.haloPause.state.value.version == pauseVersion &&
                    context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false)
            }
            manager.requestPinAppWidget(component, extras, callback).also { requested ->
                if (!requested) callback.cancel()
            }
        } catch (error: Exception) {
            callback.cancel()
            throw error
        }
    }
}

/** Android supplies the newly placed ID via the private one-shot PendingIntent. */
class EverydayWidgetPinReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != EverydayWidgetPin.REQUEST) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id <= 0 || intent.getIntArrayExtra(EverydayWidgetPin.EXISTING_IDS)?.contains(id) == true) return
        val createdAt = intent.getLongExtra(EverydayWidgetPin.CREATED_AT, 0)
        if (System.currentTimeMillis() - createdAt !in 0..EverydayWidgetPin.MAX_AGE) return
        val draft = intent.getStringExtra(EverydayWidgetPin.DRAFT)?.takeIf { it.length <= 4096 } ?: return
        val revision = intent.getLongExtra(EverydayWidgetPin.ADULT_REVISION, -1)
        val pauseVersion = intent.getStringExtra(EverydayWidgetPin.PAUSE_VERSION)
        val pending = goAsync()
        val app = context.applicationContext
        scope.launch {
            try {
                Graph.init(app)
                (app as? HaloApplication)?.awaitOrganizerBootstrap()
                val kind = EverydayWidgetProviders.kind(app, id) ?: return@launch
                val fields = JSONObject(draft)
                val config = EverydayCardConfig.from(fields.keys().asSequence().associateWith(fields::getString), kind)
                val store = EverydayWidgetStore(app)
                if (kind == config.kind && store.get(id) == null) {
                    store.save(id, null, config) {
                        Graph.kidsPreferences.requireAdultAction(revision)
                        com.halo.app.ui.theme.HaloLayoutLock.get(app).requireUnlocked()
                        if (config.adaptiveDashboard) {
                            check(!Graph.haloPause.isPaused() && Graph.haloPause.state.value.version == pauseVersion &&
                                app.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false)
                        }
                    }
                    EverydayWidgetUpdates.maintain(app)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Android retains its installed default widget when a draft is no longer valid.
                EverydayWidgetUpdates.enqueue(app)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object { val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
