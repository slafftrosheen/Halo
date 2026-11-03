package com.halo.app.nativeactions

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.halo.app.di.tools.*
import com.halo.app.everyday.presentation.*
import org.json.JSONArray
import org.json.JSONObject

/** Launcher requests and configuration use the same registered providers and durable instance owner as touch. */
class EverydayWidgetTools(private val context: Context,private val requireCurrent: ()->Unit) {
    suspend fun execute(name: String,args: Map<String,Any?>): JSONObject {
        requireCurrent()
        val store=EverydayWidgetStore(context)
        return when(name) {
            "halo_widget_query" -> JSONObject().put("instances",JSONArray(EverydayWidgetProviders.ids(context).sorted().take(100).map {id ->
                val kind=requireNotNull(EverydayWidgetProviders.kind(context,id))
                val instance=store.get(id) ?: EverydayWidgetInstance(id,0,EverydayCardConfig(kind))
                JSONObject().put("id",id).put("version",instance.version).put("config",JSONObject(instance.config.fields()))
            })).put("omittedInstances",(EverydayWidgetProviders.ids(context).size-100).coerceAtLeast(0)).put("pinSupported",android.os.Build.VERSION.SDK_INT>=26 && AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported)
            "halo_widget_configure" -> {
                val id=args.long("id").toInt();val version=args.long("version")
                val kind=requireNotNull(EverydayWidgetProviders.kind(context,id))
                val current=store.get(id) ?: EverydayWidgetInstance(id,0,EverydayCardConfig(kind))
                check(current.version==version)
                val config=EverydayCardSettings.patch(current.config,args.map("config"))
                // Resolve configured source/item choices using current local authority; a config never grants access.
                val snapshot=readEverydaySnapshot(context)
                config.recordId?.let {id -> require(snapshot.records.any {it.id==id && it.deletedAt==null})}
                listOfNotNull(config.sourceId?.let {it to if(kind==EverydayCardKind.HEADLINES) com.halo.app.everyday.collectors.CollectorKind.FEED else com.halo.app.everyday.collectors.CollectorKind.WEATHER},
                    config.headlineSourceId?.let {it to com.halo.app.everyday.collectors.CollectorKind.FEED}).forEach { (id,kind) ->
                    require(snapshot.sources.any {it.source.id==id && it.source.kind==kind && it.source.selected && it.source.enabled && !it.source.muted})
                }
                val saved=store.save(id,version.takeIf {it>0},config,allowed=requireCurrent)
                val painted=EverydayWidgetPainter.paint(context,listOf(id))
                runCatching {EverydayWidgetUpdates.maintain(context)}
                JSONObject().put("id",id).put("version",saved.version).put("saved",true).put("painted",painted).put("humanViewed",false)
            }
            "halo_widget_pin" -> {
                val kind=EverydayCardKind.valueOf(args.string("kind"))
                val provider=EverydayWidgetProviders.kinds.entries.single {it.value==kind}.key
                val manager=AppWidgetManager.getInstance(context)
                require(manager.isRequestPinAppWidgetSupported)
                requireCurrent()
                JSONObject().put("requested",manager.requestPinAppWidget(ComponentName(context,provider),null,null))
                    .put("placed",false).put("receipt","REQUESTED")
            }
            else -> throw IllegalArgumentException("Unknown widget operation")
        }.also {requireCurrent()}
    }
}
