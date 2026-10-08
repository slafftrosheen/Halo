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
    private val accessories: com.halo.app.audio.AndroidAudioAccessoryRegistry,
    private val wear: StateFlow<List<InteractionEndpointState>>,
    private val settings: StateFlow<com.halo.app.audio.VoiceExperienceSettings>,
    private val managedAudioFacet: (com.halo.app.audio.AndroidAudioAccessory) -> Boolean = { false },
) {
    private val _endpoints = MutableStateFlow<List<InteractionEndpointState>>(emptyList())
    val endpoints = _endpoints.asStateFlow()
    init { scope.launch {
        combine(catalogue.snapshot, presence.presenceMap, sessions.stateRevision, accessories.accessories, combine(wear, settings) { nodes, _ -> nodes }) { _, _, _, _, _ -> Unit }.collect {
            _endpoints.value = listOf(InteractionEndpointState(InteractionEndpoint.Phone, true, "Phone",
                InteractionEndpointCapabilities(audioInput = true, audioOutput = true, visualOutput = true, controls = true)),
                InteractionEndpointState(InteractionEndpoint.AndroidAssistant, true, "Android assistant",
                    InteractionEndpointCapabilities(visualOutput = true, controls = true))) +
                accessories.accessories.value.filter { settings.value.useConnectedAudioAccessories && !managedAudioFacet(it) }.map { accessory -> InteractionEndpointState(
                    InteractionEndpoint.AudioAccessory(accessory.id, accessory.label, accessory.kind), accessory.canOutput, accessory.label,
                    InteractionEndpointCapabilities(audioInput = accessory.canInput, audioOutput = accessory.canOutput), accessory.persistence) } +
                wear.value + sessions.registry.getSavedDevices().map { record ->
                    val endpoint = InteractionEndpoint.ManagedDevice(record.identity.deviceId, record.identity.displayName)
                    InteractionEndpointState(endpoint, ready(endpoint), record.identity.displayName.ifBlank { "Halo device" },
                        InteractionEndpointCapabilities(
                            audioInput = catalogue.snapshot.value.capabilities.any { it.endpointId == endpoint.deviceId && it.id.value == "device.audio.input" && it.availability == CapabilityAvailability.AVAILABLE },
                            audioOutput = ready(endpoint),
                            controls = sessions.registry.getDevice(endpoint.deviceId)?.descriptor?.kind == DeviceKind.HEYCYAN_GLASSES && sessions.getSession(endpoint.deviceId)?.state?.value in setOf(SessionState.CONNECTED, SessionState.READY),
                            camera = catalogue.snapshot.value.capabilities.any { it.endpointId == endpoint.deviceId && it.id.value == "device.camera.still" && it.availability == CapabilityAvailability.AVAILABLE }))
                }.filter { state -> catalogue.snapshot.value.capabilities.any { it.endpointId == (state.endpoint as InteractionEndpoint.ManagedDevice).deviceId && it.id.value == "device.audio.output" } ||
                    sessions.registry.getDevice((state.endpoint as InteractionEndpoint.ManagedDevice).deviceId)?.descriptor?.kind == DeviceKind.HEYCYAN_GLASSES }
        }
    } }
    fun ready(endpoint: InteractionEndpoint): Boolean = when (endpoint) {
        is InteractionEndpoint.ManagedDevice -> eligible(endpoint.deviceId, catalogue.snapshot.value.capabilities,
            presence.getPresence(endpoint.deviceId)?.state, sessions.getSession(endpoint.deviceId)?.state?.value)
        is InteractionEndpoint.AudioAccessory -> settings.value.useConnectedAudioAccessories && accessories.find(endpoint.accessoryId)?.canOutput == true
        is InteractionEndpoint.WearOs -> wear.value.any { sameEndpoint(it.endpoint, endpoint) && it.ready }
        else -> true
    }
}
internal fun eligible(id: String, capabilities: List<CapabilityInstance>, presence: DevicePresenceState?, session: SessionState?): Boolean =
    presence in setOf(DevicePresenceState.CONNECTED, DevicePresenceState.READY) && session in setOf(SessionState.CONNECTED, SessionState.READY) &&
        capabilities.any { it.endpointId == id && it.id.value == "device.audio.output" && it.availability == CapabilityAvailability.AVAILABLE }
