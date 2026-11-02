package com.halo.app.nativeactions

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.halo.app.HaloApplication
import com.halo.app.di.ManualDependencyGraph as Graph
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

internal data class WidgetEntryRequest(val id: Int,val version: Long,val action: String,val payload: String,val sequence: Long)

/** The immutable request survives recreation until the resumed shell consumes its destination. */
internal class EverydayWidgetEntryQueue(private val saved: SavedStateHandle,scope: CoroutineScope,
    private val destinations: MutableStateFlow<String?>,private val resolve: suspend (WidgetEntryRequest)->String,
    private val submitted: (String)->Unit = {},private val failed: ()->Unit = {},navigation: StateFlow<Long>? = null) {
    private val pending=saved.getStateFlow<Bundle?>("widget.pendingEntry",null)
    init {
      navigation?.let {revisions ->scope.launch(start=CoroutineStart.UNDISPATCHED) {revisions.drop(1).collect {cancelPending()}}}
      scope.launch {
        pending.collectLatest { bundle ->
            if(bundle==null) return@collectLatest
            val request=WidgetEntryRequest(bundle.getInt("id"),bundle.getLong("version"),bundle.getString("action").orEmpty(),bundle.getString("payload").orEmpty(),bundle.getLong("sequence"))
            try {
                val route=resolve(request)
                currentCoroutineContext().ensureActive()
                if(pending.value?.getLong("sequence")!=request.sequence) return@collectLatest
                saved["widget.submittedRoute"]=route
                submitted(route);destinations.value=route
                // Claim-before-navigation is the existing resumed-shell protocol. Retain the request before that claim.
                destinations.first { it!=route }
                clear(request.sequence)
            } catch(cancelled: CancellationException) { throw cancelled }
              catch(_: Exception) { if(pending.value?.getLong("sequence")==request.sequence) { failed();clear(request.sequence) } }
        }
    } }
    fun offer(id: Int,version: Long,action: String,payload: String) {
        require(id>0 && version>=0 && payload.length in 1..4096 && (action=="open" || action.matches(Regex("row:[0-4]"))))
        saved.get<String>("widget.submittedRoute")?.let { destinations.compareAndSet(it,null) }
        saved["widget.submittedRoute"]=null
        val sequence=(saved.get<Long>("widget.entrySequence") ?: 0)+1
        saved["widget.entrySequence"]=sequence
        saved["widget.pendingEntry"]=Bundle().apply { putInt("id",id);putLong("version",version);putString("action",action);putString("payload",payload);putLong("sequence",sequence) }
    }
    fun cancelPending() {
        saved.get<String>("widget.submittedRoute")?.let { destinations.compareAndSet(it,null) }
        saved["widget.pendingEntry"]=null;saved["widget.submittedRoute"]=null
    }
    private fun clear(sequence: Long) { if(pending.value?.getLong("sequence")==sequence) { saved["widget.pendingEntry"]=null;saved["widget.submittedRoute"]=null } }
}

class EverydayWidgetEntryViewModel(application: Application,saved: SavedStateHandle) : AndroidViewModel(application) {
    private val queue=EverydayWidgetEntryQueue(saved,viewModelScope,Graph.requestedHaloScreen,resolve={ request ->
        (application as HaloApplication).awaitOrganizerBootstrap()
        withContext(Dispatchers.IO) { resolveEverydayWidgetEntry(application,request.id,request.version,request.payload) }
    },submitted={Graph.explicitHaloScreenRequested=true},failed={android.widget.Toast.makeText(application,
        application.nativeUi("This widget changed or its item is unavailable. Open Halo and review the widget."),android.widget.Toast.LENGTH_LONG).show()},navigation=Graph.uiNavigationRevision)
    fun offer(id: Int,version: Long,action: String,payload: String)=queue.offer(id,version,action,payload)
    fun cancelPending()=queue.cancelPending()
}
