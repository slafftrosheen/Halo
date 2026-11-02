package com.halo.app.nativeactions

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.app.everyday.presentation.*
import com.halo.app.everyday.collectors.CollectorKind
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class WidgetApplyState(val busy: Boolean=false,val completed: Boolean=false,val error: Boolean=false)

/** Survives rotation; a recovered immutable operation receipt completes placement rather than rebasing a draft. */
internal class WidgetApplySession(private val scope: CoroutineScope,private val committed: suspend ()->Boolean,
    private val paint: suspend ()->Unit) {
    private val mutable=MutableStateFlow(WidgetApplyState())
    val state=mutable.asStateFlow()
    private var job: Job?=null
    private suspend fun finishCommitted() {
        try {paint()}
        catch(cancelled: CancellationException) {if(!currentCoroutineContext().isActive) throw cancelled}
        catch(_: Exception) { }
        mutable.value=WidgetApplyState(completed=true)
    }
    fun run(save: (suspend ()->Unit)? = null) {
        if(job?.isActive==true || mutable.value.completed) return
        mutable.value=WidgetApplyState(busy=true)
        job=scope.launch {
            try {
                if(!committed()) {
                    if(save==null) {mutable.value=WidgetApplyState();return@launch}
                    save()
                }
                check(committed())
                // A paint failure does not undo verified durable settings. OS refresh can recover it.
                finishCommitted()
            } catch(cancelled: CancellationException) {
                if(!currentCoroutineContext().isActive) throw cancelled
                // Snapshot/family invalidation may use CancellationException without cancelling this retained owner.
                val durable=runCatching {committed()}.getOrDefault(false)
                mutable.value=WidgetApplyState(completed=durable,error=!durable)
            }
            catch(_: Exception) {
                // A newer activity may have observed no receipt just before the original operation committed.
                val completed=runCatching {committed()}.getOrDefault(false)
                if(completed) {
                    finishCommitted()
                } else mutable.value=WidgetApplyState(error=true)
            }
        }
    }
}

class EverydayWidgetApplyOperation(application: Application,private val saved: SavedStateHandle): AndroidViewModel(application) {
    private var widgetId=0
    val operationId: String=saved.get<String>("operation") ?: java.util.UUID.randomUUID().toString().also {saved["operation"]=it}
    private val session=WidgetApplySession(viewModelScope,
        committed={withContext(Dispatchers.IO) {EverydayWidgetStore(getApplication()).completed(widgetId,operationId)}},
        paint={withContext(Dispatchers.IO) {EverydayWidgetPainter.paint(getApplication(),listOf(widgetId));runCatching {EverydayWidgetUpdates.maintain(getApplication())}}})
    internal val state=session.state
    fun bind(id: Int) {check(widgetId==0 || widgetId==id);widgetId=id;session.run()}
    fun submit(kind: EverydayCardKind,expected: Long?,config: EverydayCardConfig,sourceVersions: Map<String,Long>) {
        val application=getApplication<HaloApplication>()
        session.run {
            application.awaitOrganizerBootstrap()
            val epoch=Graph.kidsPreferences.adultActionRevision()
            withContext(Dispatchers.IO) {
                check(EverydayWidgetProviders.kind(application,widgetId)==kind)
                require(config.kind==kind && config.hidePrivate)
                listOfNotNull(config.sourceId?.let {it to if(kind==EverydayCardKind.HEADLINES) CollectorKind.FEED else CollectorKind.WEATHER},
                    config.headlineSourceId?.let {it to CollectorKind.FEED}).forEach { (id,requiredKind) ->
                    val source=requireNotNull(Graph.collectorRepository.state(id)).source
                    require(source.version==sourceVersions[id] && source.selected && source.enabled && !source.muted && source.kind==requiredKind)
                }
                EverydayWidgetStore(application).save(widgetId,expected,config,operationId) {Graph.kidsPreferences.requireAdultAction(epoch)}
            }
        }
    }
}
