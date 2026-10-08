package com.halo.app.controls

import com.halo.app.di.ManualDependencyGraph as Graph
import com.halo.runtime.cognition.continuity.*
import com.halo.sdk.host.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Application composition only; typed actions delegate to the same owners used by existing screens. */
class ExistingAccessoryActionOwners {
    private val navigation = Channel<String>(Channel.RENDEZVOUS)
    val navigationEvents = navigation.receiveAsFlow()
    private val replay by lazy { CompletedAnswerReplay(Graph.eventBus, Graph.infra.scope) { event ->
        if (event.target != com.halo.sdk.runtime.SpeechOutputTarget.HostDefault) event.target
        else {
            val state = Graph.haloSpeechOutputCoordinator.session.state.value as? com.halo.app.audio.HaloAudioSessionState.Speaking
            if (state?.transactionId != event.transactionId) null
            else Graph.audioAccessoryRegistry.accessories.value.firstOrNull {
                state.route.androidDeviceId != null && (state.route.androidDeviceId == it.outputDeviceId || state.route.androidDeviceId == it.communicationDeviceId)
            }?.let { com.halo.sdk.runtime.SpeechOutputTarget.Device(it.id, state.route.androidDeviceId, it.label, it.kind) }
                ?: com.halo.sdk.runtime.SpeechOutputTarget.Phone.takeIf { state.route.androidDeviceType == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
    } }
    fun start() { replay }
    suspend fun execute(selection: AccessoryActionSelection, endpoint: ControlEndpoint): AccessoryActionResult {
        fun result(outcome: AccessoryActionOutcome, message: String) = AccessoryActionResult(outcome, message)
        suspend fun open(route: String): AccessoryActionResult {
            // Foreground consumer required; never buffer navigation for a later reconnect/resume.
            return if (navigation.trySend(route).isSuccess) result(AccessoryActionOutcome.OPENED_FOR_REVIEW, "Opened on phone")
            else result(AccessoryActionOutcome.UNAVAILABLE, "Open Halo on your phone")
        }
        return when (selection.action) {
            AccessoryAction.StopSpeaking -> { Graph.haloSpeechOutputCoordinator.stop("Accessory control"); result(AccessoryActionOutcome.EXECUTED, "Speech stopped") }
            AccessoryAction.RepeatLastAnswer -> replay.repeat()
            AccessoryAction.CreateNote -> open("notebook").also {
                if (it.outcome == AccessoryActionOutcome.OPENED_FOR_REVIEW) Graph.notebookViewModel.createNote()
            }
            AccessoryAction.OpenStudy -> open("study")
            AccessoryAction.OpenCurrentInteraction -> open("home")
            AccessoryAction.OpenRecentCapture -> Graph.recentCaptureProjection.recent.value?.let { open("notebook/note/${it.objectId}") }
                ?: result(AccessoryActionOutcome.UNAVAILABLE, "No recent capture")
            AccessoryAction.ReviewRoutine -> {
                val id = selection.routineId?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{1,160}")) }
                if (id == null || Graph.routineRepository.get(com.halo.runtime.cognition.routine.RoutineId(id)) == null)
                    result(AccessoryActionOutcome.UNAVAILABLE, "Routine unavailable")
                else open("automation?routineId=$id")
            }
            AccessoryAction.ContinueOnThisDevice -> {
                val id = Graph.interactionContinuity.state.value.interactionId
                val target = Graph.continuityEndpoints.endpoints.value.firstOrNull {
                    val managed = it.endpoint as? InteractionEndpoint.ManagedDevice
                    managed != null && managed.deviceId == Graph.hostGraph.heyCyanDriver.activeDeviceId &&
                        endpoint.deviceClass == "HEYCYAN" && Graph.hostGraph.heyCyanHost.controlEndpointId == endpoint.id
                }?.endpoint
                if (id == null || target == null) result(AccessoryActionOutcome.UNAVAILABLE, "No active interaction or available endpoint")
                else if (Graph.interactionContinuity.handoff(id, target) == InteractionHandoffStatus.APPLIED)
                    result(AccessoryActionOutcome.EXECUTED, "Continued on this device")
                else result(AccessoryActionOutcome.UNAVAILABLE, "Endpoint unavailable")
            }
            AccessoryAction.TakePhoto, AccessoryAction.CaptureAndAsk -> {
                val host = Graph.hostGraph.heyCyanHost
                if (endpoint.id != host.controlEndpointId || endpoint.epoch != host.controlEpoch || host.connectivity.value != ConnectionState.Ready)
                    result(AccessoryActionOutcome.UNAVAILABLE, "Camera disconnected")
                else confirmedControlCapture(selection.action, captureAndSave = {
                    val thumbnail = Graph.hostGraph.captureCoordinator?.captureAiThumbnail(publishForInference = false)?.getOrNull()
                    if (thumbnail == null) null
                    else {
                        val pending = Graph.captures.preparePhoneCapture()
                        var saved = false
                        try {
                            pending.file.writeBytes(thumbnail.bytes)
                            val objectId = Graph.captures.receive(pending.file, Graph.hostGraph.heyCyanDriver.activeDeviceId, publishForInference = false)
                            saved = true
                            pending.uri to objectId
                        } finally { if (!saved) Graph.captures.discardPendingCapture(pending.file) }
                    }
                }, verifiedVision = {
                    val vision = kotlinx.coroutines.withTimeoutOrNull(2_000) { com.halo.app.capture.inspectMultimodal(Graph.aiProviderRouter) }
                    vision?.packs?.any { it.descriptor.id == com.halo.sdk.runtime.capability.RuntimeCapabilityId.LOCAL_VISION &&
                        it.state == com.halo.sdk.runtime.capability.RuntimeCapabilityState.READY } == true
                }, attachForQuestion = { saved ->
                    if (Graph.visualAttachments.state.value != null) false
                    else {
                        Graph.visualAttachments.attach(saved.first, "Wearable capture", com.halo.sdk.runtime.multimodal.MediaProvenance.WORKSPACE, saved.second)
                        Graph.homeViewModel.captureMessage.value = "Photo attached. Ask a question when ready; no image has been sent to a provider."
                        true
                    }
                }, openQuestion = { open("home") })
            }
            AccessoryAction.TalkToHalo -> result(AccessoryActionOutcome.UNAVAILABLE, "Use the native assistant or verified wearable voice button")
            else -> result(AccessoryActionOutcome.IGNORED, "Managed by system")
        }
    }
}
