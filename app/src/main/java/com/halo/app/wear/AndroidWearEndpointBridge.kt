package com.halo.app.wear

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.wearable.*
import com.halo.app.continuity.FollowMeOptions
import com.halo.cognition.language.InteractionId
import com.halo.runtime.cognition.agent.AgentRunId
import com.halo.runtime.cognition.continuity.*
import com.halo.runtime.cognition.interaction.*
import com.halo.sdk.runtime.AssistantResponsePublished
import com.halo.sdk.runtime.EventBus
import java.time.Instant
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class WatchDelivery(val interactionId: String, val label: String)

/** The signed, nearby companion gets only the surface selected for that exact foreground node. */
class AndroidWearEndpointBridge(
    private val context: Context,
    private val surfaces: InteractionSurfaceStore,
    private val responses: InteractionResponseHandler,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val events: EventBus,
    private val follow: StateFlow<FollowMeOptions>,
    private val continuity: () -> StateFlow<InteractionContinuityState>,
    private val audienceAllowed: () -> Boolean,
    private val canPresentReply: suspend (AgentRunId?) -> Boolean,
) : MessageClient.OnMessageReceivedListener, CapabilityClient.OnCapabilityChangedListener {
    private val state = MutableStateFlow<List<InteractionEndpointState>>(emptyList())
    val endpoints = state.asStateFlow()
    val apiAvailable = MutableStateFlow(false)
    private val _foreground = MutableStateFlow<Map<String, Long>>(emptyMap())
    val foreground = _foreground.asStateFlow()
    private val _delivered = MutableStateFlow<WatchDelivery?>(null)
    val delivered = _delivered.asStateFlow()
    private val lifecycle = Mutex()
    private val offers = Mutex()
    private var job: Job? = null
    private val result = MutableStateFlow<InteractionSurface?>(null)
    private val shown = mutableMapOf<String, InteractionSurface?>()

    fun start() {
        if (job?.isActive == true || !enabled()) return
        job = scope.launch {
            lifecycle.withLock {
                try {
                    Wearable.getMessageClient(context).addListener(this@AndroidWearEndpointBridge).await()
                    Wearable.getCapabilityClient(context).addListener(this@AndroidWearEndpointBridge, CAPABILITY).await()
                    refresh()
                    coroutineScope {
                        val route = continuity()
                        launch {
                            events.observe(AssistantResponsePublished::class.java).collect { reply ->
                                val turn = reply.interactionId ?: return@collect
                                val current = route.value
                                if (!reply.continuationPending && selected(current, turn) != null && canPresentReply(current.runId)) {
                                    val preview = reply.text.take(1500)
                                    result.value = InteractionSurface(InteractionSurfaceId(reply.transactionId.take(160)),
                                        null, InteractionId(turn), null, InteractionSurfaceKind.RESULT, "Halo", preview,
                                        InteractionContent.Result(preview), emptyList(), InteractionPresentationPolicy(),
                                        Instant.now(), Instant.now().plusSeconds(60),
                                        provenance = InteractionSurfaceProvenance(InteractionSurfaceSource.SYSTEM))
                                }
                            }
                        }
                        launch {
                            combine(surfaces.active, endpoints, result, route, follow) { _, _, _, _, _ -> Unit }
                                .collect { sendForeground() }
                        }
                        launch {
                            while (isActive) {
                                delay(5_000)
                                val now = SystemClock.elapsedRealtime()
                                _foreground.update { seen -> seen.filterValues { now - it in 0 until WATCH_FRESH_MS } }
                                sendForeground()
                            }
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { apiAvailable.value = false }
                finally {
                    runCatching { Wearable.getMessageClient(context).removeListener(this@AndroidWearEndpointBridge) }
                    runCatching { Wearable.getCapabilityClient(context).removeListener(this@AndroidWearEndpointBridge) }
                    // Cancellation of an old job cannot interleave with a restarted bridge's state initialization.
                    state.value = emptyList()
                    _foreground.value = emptyMap()
                    _delivered.value = null
                    result.value = null
                    withContext(NonCancellable) { offers.withLock { shown.clear() } }
                }
            }
        }
    }

    fun foregroundEndpoint(): InteractionEndpoint.WearOs? {
        if (!enabled() || !audienceAllowed()) return null
        return endpoints.value.mapNotNull { it.endpoint as? InteractionEndpoint.WearOs }
            .singleOrNull { active(it.nodeId) }
    }

    fun active(nodeId: String): Boolean = _foreground.value[nodeId]?.let { seen ->
        SystemClock.elapsedRealtime() - seen in 0 until WATCH_FRESH_MS
    } == true

    private fun selected(route: InteractionContinuityState, turn: String?): InteractionEndpoint.WearOs? {
        if (turn == null || route.interactionId != turn || !enabled() || !audienceAllowed()) return null
        if (route.automaticPresentation && !follow.value.enabled) return null
        val target = route.presentationEndpoint as? InteractionEndpoint.WearOs ?: return null
        return target.takeIf { active(it.nodeId) && registered(it.nodeId) }
    }

    private fun latest(nodeId: String): InteractionSurface? {
        val route = continuity().value
        val turn = route.interactionId
        if (selected(route, turn)?.nodeId != nodeId) return null
        val pending = surfaces.active.value.lastOrNull { it.interactionId?.value == turn && it.inputRequestId != null }
        if (pending != null) return pending.takeIf {
            follow.value.watchApprovals && WearSurfaceCodec.canConfirm(it)
        }
        return result.value?.takeIf { it.interactionId?.value == turn && it.expiresAt?.isAfter(Instant.now()) == true }
    }

    private suspend fun refresh() {
        val capability = Wearable.getCapabilityClient(context).getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE).await()
        apiAvailable.value = true
        project(capability)
    }

    private fun project(capability: CapabilityInfo) {
        state.value = capability.nodes.filter { it.isNearby }.map { node ->
            InteractionEndpointState(InteractionEndpoint.WearOs(node.id, node.displayName), true, node.displayName,
                InteractionEndpointCapabilities(visualOutput = true, controls = true, haptics = true), EndpointPersistence.SESSION_ONLY)
        }
        _foreground.update { seen -> seen.filterKeys(::registered) }
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) = project(capabilityInfo)

    private fun registered(id: String) = endpoints.value.any {
        it.ready && (it.endpoint as? InteractionEndpoint.WearOs)?.nodeId == id
    }

    private suspend fun nearby(id: String) = Wearable.getNodeClient(context).connectedNodes.await()
        .any { it.id == id && it.isNearby }

    private val transport = NearbyWearContentTransport(
        nearby = { enabled() && registered(it) && nearby(it) },
        transmit = { id, path, bytes -> Wearable.getMessageClient(context).sendMessage(id, path, bytes).await(); Unit },
    )

    private suspend fun offer(nodeId: String, force: Boolean = false) = offers.withLock {
        val surface = latest(nodeId)
        if (!force && shown[nodeId] == surface) return@withLock
        val sent = transport.sendSurface(nodeId, surface) { candidate ->
            // Recheck stop, lock, child mode, route and exact content after the nearby IPC.
            candidate == surface && (candidate == null || candidate == latest(nodeId))
        }
        if (sent) {
            shown[nodeId] = surface
            if (surface?.kind == InteractionSurfaceKind.RESULT) {
                val turn = surface.interactionId?.value ?: return@withLock
                val label = endpoints.value.firstOrNull { (it.endpoint as? InteractionEndpoint.WearOs)?.nodeId == nodeId }?.label
                if (!label.isNullOrBlank()) _delivered.value = WatchDelivery(turn, label)
            }
        }
    }

    private suspend fun sendForeground() {
        val nodes = endpoints.value.mapNotNull { (it.endpoint as? InteractionEndpoint.WearOs)?.nodeId }
        offers.withLock { shown.keys.retainAll(nodes.toSet()) }
        nodes.forEach { node ->
            try { offer(node) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* The optional companion may have moved out of reach. */ }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path !in setOf("/halo/response", "/halo/ready", "/halo/open") || event.data.size > 4096) return
        scope.launch {
            val node = event.sourceNodeId
            try {
                if (!enabled() || !registered(node) || !nearby(node) || !audienceAllowed()) return@launch
                when (event.path) {
                    "/halo/ready" -> {
                        _foreground.update { it + (node to SystemClock.elapsedRealtime()) }
                        offer(node, force = true)
                    }
                    "/halo/open" -> {
                        if (!active(node)) return@launch
                        context.startActivity(android.content.Intent(context, com.halo.app.MainActivity::class.java)
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    else -> {
                        if (!active(node)) return@launch
                        val response = WearSurfaceCodec.response(event.data)
                        if (response !is InteractionResponse.Confirmed && response !is InteractionResponse.Denied) return@launch
                        val offered = offers.withLock { shown[node] } ?: return@launch
                        if (offered.id != response.surfaceId || offered != latest(node) || !WearSurfaceCodec.canConfirm(offered)) return@launch
                        responses.respond(InteractionResponseSubmission(response, InteractionEndpoint.WearOs(node)))
                        offer(node)
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { /* Malformed or unavailable. No action admitted. */ }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        state.value = emptyList()
        _foreground.value = emptyMap()
        _delivered.value = null
        result.value = null
    }

    private companion object {
        const val CAPABILITY = "halo_wear_endpoint"
        const val WATCH_FRESH_MS = 40_000L
    }
}
