package com.halo.app.continuity

import com.halo.runtime.cognition.continuity.*
import com.halo.runtime.cognition.device.*
import com.halo.runtime.cognition.tools.*
import com.halo.sdk.runtime.embodiment.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Reads existing owners. Opening a chooser never connects a saved device. */
class AndroidContinuityEndpoints(
    private val catalogue: CapabilityCatalogueSource,
    private val presence: DevicePresenceRepository,
    private val sessions: DeviceSessionManager,
    scope: CoroutineScope,
) {
    private val _endpoints = MutableStateFlow<List<InteractionEndpointState>>(emptyList())
    val endpoints = _endpoints.asStateFlow()
    init { scope.launch {
        combine(catalogue.snapshot, presence.presenceMap, sessions.stateRevision) { _, _, _ -> Unit }.collect {
            _endpoints.value = listOf(InteractionEndpointState(InteractionEndpoint.Phone, true, "Phone")) +
                sessions.registry.getSavedDevices().map { record ->
                    val endpoint = InteractionEndpoint.Wearable(record.identity.deviceId, record.identity.displayName)
                    InteractionEndpointState(endpoint, ready(endpoint), record.identity.displayName.ifBlank { "Wearable" })
                }.filter { state -> catalogue.snapshot.value.capabilities.any { it.endpointId == (state.endpoint as InteractionEndpoint.Wearable).deviceId && it.id.value == "device.audio.output" } ||
                    sessions.registry.getDevice((state.endpoint as InteractionEndpoint.Wearable).deviceId)?.descriptor?.kind == DeviceKind.HEYCYAN_GLASSES }
        }
    } }
    fun ready(endpoint: InteractionEndpoint): Boolean = when (endpoint) {
        is InteractionEndpoint.Wearable -> eligible(endpoint.deviceId, catalogue.snapshot.value.capabilities,
            presence.getPresence(endpoint.deviceId)?.state, sessions.getSession(endpoint.deviceId)?.state?.value)
        else -> true
    }
}
internal fun eligible(id: String, capabilities: List<CapabilityInstance>, presence: DevicePresenceState?, session: SessionState?): Boolean =
    presence in setOf(DevicePresenceState.CONNECTED, DevicePresenceState.READY) && session in setOf(SessionState.CONNECTED, SessionState.READY) &&
        capabilities.any { it.endpointId == id && it.id.value == "device.audio.output" && it.availability == CapabilityAvailability.AVAILABLE }
