package com.halo.app.wear

import android.content.Context
import com.google.android.gms.wearable.*
import com.halo.runtime.cognition.continuity.*
import com.halo.runtime.cognition.interaction.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.tasks.await

/** Optional lifecycle owner. No persistent DataItems and no remote user-content transport. */
class AndroidWearEndpointBridge(private val context: Context,
    private val surfaces: InteractionSurfaceStore, private val responses: InteractionResponseHandler,
    private val scope: CoroutineScope, private val enabled: () -> Boolean = { true },
    private val events: com.halo.sdk.runtime.EventBus? = null) : MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
    private val state = MutableStateFlow<List<InteractionEndpointState>>(emptyList())
    val endpoints = state.asStateFlow()
    val apiAvailable = MutableStateFlow(false)
    private val lifecycle = kotlinx.coroutines.sync.Mutex()
    private var job: Job? = null
    private val result = MutableStateFlow<InteractionSurface?>(null)
    fun start() {
        if (job?.isActive == true || !enabled()) return
        job = scope.launch {
            lifecycle.lock()
            try {
                Wearable.getMessageClient(context).addListener(this@AndroidWearEndpointBridge).await()
                Wearable.getCapabilityClient(context).addListener(this@AndroidWearEndpointBridge, "halo_wear_endpoint").await()
                refresh()
                coroutineScope {
                    events?.let { bus -> launch {
                        bus.observe(com.halo.sdk.runtime.AssistantResponsePublished::class.java).collect { response ->
                            val preview = response.text.take(1500)
                            result.value = InteractionSurface(InteractionSurfaceId(response.transactionId.take(160)), null, null, null,
                                InteractionSurfaceKind.RESULT, "Halo", preview, InteractionContent.Result(preview), emptyList(),
                                InteractionPresentationPolicy(), java.time.Instant.now(), java.time.Instant.now().plusSeconds(60),
                                provenance = InteractionSurfaceProvenance(InteractionSurfaceSource.SYSTEM))
                        }
                    } }
                    combine(surfaces.active, endpoints, result) { active, nodes, preview -> (active.lastOrNull() ?: preview) to nodes }
                        .collect { (surface, nodes) ->
                            nodes.forEach { node ->
                                try { send((node.endpoint as InteractionEndpoint.WearOs).nodeId, surface) }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { /* optional endpoint unavailable */ }
                            }
                        }
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { state.value = emptyList(); apiAvailable.value = false }
            finally {
                runCatching { Wearable.getMessageClient(context).removeListener(this@AndroidWearEndpointBridge) }
                runCatching { Wearable.getCapabilityClient(context).removeListener(this@AndroidWearEndpointBridge) }
                lifecycle.unlock()
            }
        }
    }
    private suspend fun refresh() {
        val capability = Wearable.getCapabilityClient(context).getCapability("halo_wear_endpoint", CapabilityClient.FILTER_REACHABLE).await()
        apiAvailable.value = true
        project(capability)
    }
    private fun project(capability: CapabilityInfo) {
        state.value = capability.nodes.filter { it.isNearby }.map { node ->
            InteractionEndpointState(InteractionEndpoint.WearOs(node.id, node.displayName), true, node.displayName,
                InteractionEndpointCapabilities(visualOutput = true, controls = true, haptics = true), EndpointPersistence.SESSION_ONLY)
        }
    }
    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) = project(capabilityInfo)
    private suspend fun nearby(id: String): Boolean = Wearable.getNodeClient(context).connectedNodes.await().any { it.id == id && it.isNearby }
    private val transport = NearbyWearContentTransport(
        nearby = { enabled() && nearby(it) },
        transmit = { id, path, bytes -> Wearable.getMessageClient(context).sendMessage(id, path, bytes).await(); Unit })
    suspend fun send(id: String, surface: InteractionSurface?): Boolean {
        return transport.sendSurface(id, surface)
    }
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path !in setOf("/halo/response", "/halo/ready", "/halo/open") || event.data.size > 4096) return
        scope.launch {
            try {
                if (!enabled() || !nearby(event.sourceNodeId) || endpoints.value.none { (it.endpoint as? InteractionEndpoint.WearOs)?.nodeId == event.sourceNodeId }) return@launch
                if (event.path == "/halo/ready") { send(event.sourceNodeId, surfaces.active.value.lastOrNull() ?: result.value); return@launch }
                if (event.path == "/halo/open") {
                    context.startActivity(android.content.Intent(context, com.halo.app.MainActivity::class.java)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    return@launch
                }
                val response = WearSurfaceCodec.response(event.data)
                if (response is InteractionResponse.Confirmed && surfaces.active.value.none { it.id == response.surfaceId && WearSurfaceCodec.canConfirm(it) }) return@launch
                responses.respond(InteractionResponseSubmission(response, InteractionEndpoint.WearOs(event.sourceNodeId)))
                send(event.sourceNodeId, surfaces.active.value.lastOrNull())
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* malformed or unavailable; no execution */ }
        }
    }
    fun stop() { job?.cancel(); job = null; state.value = emptyList(); result.value = null }
}
