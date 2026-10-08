package com.halo.app.controls

import android.util.Log
import com.halo.drivers.heycyan.HeyCyanHost
import com.halo.sdk.host.*
import com.halo.sdk.runtime.Storage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ControlFeedback(val control: String = "None", val gesture: String = "None", val evidence: String = "None",
    val action: String = "None", val outcome: String = "None")

/** One event consumer, one gesture state, one dispatcher. Identify is foreground, observation-only. */
class AccessoryControls(storage: Storage, private val scope: CoroutineScope, private val dispatcher: AccessoryActionDispatcher) {
    val mappings = AccessoryControlMappingRepository(storage)
    private val interpreter = AccessoryGestureInterpreter()
    private val mutex = Mutex()
    private val mutableEndpoints = MutableStateFlow<List<ControlEndpoint>>(emptyList())
    val endpoints = mutableEndpoints.asStateFlow()
    private val mutableFeedback = MutableStateFlow(ControlFeedback())
    val feedback = mutableFeedback.asStateFlow()
    private val workers = mutableMapOf<String, Job>()
    private val actions = mutableMapOf<String, MutableList<Job>>()
    @Volatile private var identifying: String? = null
    fun identify(endpointId: String?) { identifying = endpointId }

    fun attach(host: HeyCyanHost) {
        if (workers["heycyan"]?.isActive == true) return
        workers["heycyan"] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            launch { host.connectivity.collect { state ->
                val endpoint = ControlEndpoint(host.controlEndpointId, "HEYCYAN", host.controlEpoch, host.controlIdentityDurable, state == ConnectionState.Ready,
                    emptySet(), managedActions)
                update(endpoint)
                if (endpoint.connected) launch {
                    val vision = withTimeoutOrNull(2_000) { com.halo.app.capture.inspectMultimodal(com.halo.app.di.ManualDependencyGraph.aiProviderRouter) }
                    if (vision?.packs?.any { it.descriptor.id == com.halo.sdk.runtime.capability.RuntimeCapabilityId.LOCAL_VISION &&
                            it.state == com.halo.sdk.runtime.capability.RuntimeCapabilityState.READY } == true &&
                        host.controlEpoch == endpoint.epoch && host.connectivity.value == ConnectionState.Ready)
                        update(endpoint.copy(availableActions = managedActions + AccessoryAction.CaptureAndAsk))
                }
            } }
            host.controls.collect { event -> if (event is AccessoryControlEvent.Button) receive(event) }
        }
    }
    suspend fun update(endpoint: ControlEndpoint) = mutex.withLock {
        val previous = mutableEndpoints.value.firstOrNull { it.id == endpoint.id }
        if (previous?.epoch != endpoint.epoch || !endpoint.connected) {
            actions.remove(endpoint.id)?.forEach { it.cancel() }
            previous?.let(mappings::disconnect)
            interpreter.disconnect(endpoint.id)
            if (endpoint.connected) interpreter.connect(endpoint.id, endpoint.epoch)
        }
        val effective = if (previous?.epoch == endpoint.epoch && endpoint.connected)
            endpoint.copy(supported = previous.supported + endpoint.supported) else endpoint
        mutableEndpoints.value = mutableEndpoints.value.filterNot { it.id == endpoint.id ||
            (it.deviceClass == "HEYCYAN" && endpoint.deviceClass == "HEYCYAN") } + effective
    }
    suspend fun receive(event: AccessoryControlEvent.Button) = mutex.withLock {
        var endpoint = mutableEndpoints.value.firstOrNull { it.id == event.delivery?.endpointId } ?: return@withLock
        val now = System.currentTimeMillis()
        val d = event.delivery ?: return@withLock
        if (!endpoint.connected || d.epoch != endpoint.epoch || now - event.timestamp > 2_000 || event.timestamp > now + 100) return@withLock
        // An enum declaration is not delivery evidence. Advertise the camera row only after actual verified input.
        if (endpoint.deviceClass == "HEYCYAN" && d.origin == ControlOrigin.MANAGED_DEVICE &&
            d.evidence == ControlEvidence.VERIFIED_VENDOR && event.control == AccessoryControl.CAMERA && event.action == AccessoryControlAction.SINGLE) {
            endpoint = endpoint.copy(supported = endpoint.supported + (event.control to event.action))
            mutableEndpoints.value = mutableEndpoints.value.map { if (it.id == endpoint.id) endpoint else it }
        }
        log("OBSERVED", event)
        mutableFeedback.value = ControlFeedback(event.control.name, event.action.name, d.evidence.name, "None",
            if (d.evidence in setOf(ControlEvidence.OBSERVED, ControlEvidence.UNKNOWN)) "Observed · Not assignable" else "Observed")
        if (identifying == endpoint.id) {
            mutableFeedback.value = ControlFeedback(event.control.name, event.action.name, d.evidence.name, "Identify", "Observed · no action")
            return@withLock
        }
        val bindings = mappings.device(endpoint) + mappings.defaults(endpoint.deviceClass)
        val resolved = interpreter.accept(event, now, bindings.any { it.control == event.control && it.gesture == AccessoryControlAction.DOUBLE })
        resolved.forEach { dispatch(it, endpoint) }
        if (!d.classified && resolved.isEmpty()) scope.launch { delay(350); mutex.withLock {
            interpreter.advance(System.currentTimeMillis()).forEach { resolvedEvent ->
                mutableEndpoints.value.firstOrNull { it.id == resolvedEvent.delivery?.endpointId && it.epoch == resolvedEvent.delivery?.epoch && it.connected }
                    ?.let { dispatch(resolvedEvent, it) }
            }
        } }
    }
    private fun dispatch(event: AccessoryControlEvent.Button, endpoint: ControlEndpoint) {
        if (System.currentTimeMillis() - event.timestamp > 2_000) return
        log("GESTURE_RESOLVED", event)
        val selection = ConfigurableAccessoryControlPolicy.select(event, endpoint, mappings.device(endpoint), mappings.defaults(endpoint.deviceClass))
        log("ACTION_SELECTED", event, selection.action.name)
        // No queue: a reconnect or slow action cannot replay buffered physical intent.
        val pending = actions.getOrPut(endpoint.id) { mutableListOf() }
        pending.removeAll { !it.isActive }
        if (pending.isNotEmpty() && selection.action != AccessoryAction.StopSpeaking) return
        pending += scope.launch {
            if (System.currentTimeMillis() - event.timestamp > 2_000) return@launch
            val current = endpoints.value.firstOrNull { it.id == endpoint.id && it.epoch == endpoint.epoch && it.connected }
                ?: return@launch
            val result = dispatcher.dispatch(selection, current)
            mutableFeedback.value = ControlFeedback(event.control.name, event.action.name, event.delivery!!.evidence.name, selection.action.name, result.outcome.name + " · " + result.reason)
            log(if (result.outcome == AccessoryActionOutcome.BLOCKED) "ACTION_BLOCKED" else "ACTION_COMPLETED", event, selection.action.name, result.outcome.name)
        }
    }
    private fun log(stage: String, event: AccessoryControlEvent.Button, action: String = "NONE", result: String = "NONE") {
        Log.i("ACCESSORY_CONTROL_$stage", "origin=${event.delivery?.origin} control=${event.control} gesture=${event.action} evidence=${event.delivery?.evidence} action=$action result=$result")
    }
    companion object {
        // HeyCyan's voice boundary is reserved to WearableSpeechInputBridge: never start another recorder here.
        val managedActions = setOf(AccessoryAction.StopSpeaking, AccessoryAction.RepeatLastAnswer, AccessoryAction.TakePhoto,
            AccessoryAction.OpenRecentCapture, AccessoryAction.CreateNote, AccessoryAction.OpenStudy,
            AccessoryAction.OpenCurrentInteraction, AccessoryAction.ContinueOnThisDevice,
            AccessoryAction.ReviewRoutine,
            AccessoryAction.SystemDefault, AccessoryAction.DoNothing)
    }
}
