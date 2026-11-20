package com.halo.app.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.WpsInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.wifi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class HeyCyanWifiPeer(val address: String, val name: String)
data class HeyCyanWifiPeerSelection(val deviceId: String? = null, val peers: List<HeyCyanWifiPeer> = emptyList())
class HeyCyanWifiDisabledException : IllegalStateException("Turn on Wi-Fi in Android before copying files from the glasses")

/** Android's P2P transport; explicit peer choice and fresh BLE IP are separate evidence. */
@SuppressLint("MissingPermission")
class HeyCyanWifiDirectConnection(
    private val context: Context,
    private val events: Flow<HeyCyanEvent>,
    private val activeDevice: () -> String?,
    private val scope: CoroutineScope,
) : HeyCyanWifiConnection {
    private val manager = context.getSystemService(WifiP2pManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var channel: WifiP2pManager.Channel? = null
    private var receiver: BroadcastReceiver? = null
    private var ipJob: Job? = null
    private var deviceId: String? = null
    private var selectedPeer: String? = null
    private var selection = CompletableDeferred<String>()
    private val group = MutableStateFlow<WifiP2pGroup?>(null)
    private val freshIp = MutableStateFlow<String?>(null)
    private val mutableState = MutableStateFlow(WifiConnectionState.DISCONNECTED)
    override val state = mutableState.asStateFlow()
    private val mutableSelection = MutableStateFlow(HeyCyanWifiPeerSelection())
    val peerSelection = mutableSelection.asStateFlow()
    override val requiresCredentials = false
    private var requestedConnection = false
    private var httpClient: okhttp3.OkHttpClient? = null

    fun selectPeer(deviceId: String, address: String) {
        val offered = mutableSelection.value
        if (offered.deviceId != deviceId || activeDevice() != deviceId ||
            offered.peers.none { it.address == address }) return
        if (selection.complete(address)) mutableSelection.value = HeyCyanWifiPeerSelection()
    }

    override suspend fun prepare(deviceId: String): Unit = withContext(Dispatchers.Main.immediate) {
        check(this@HeyCyanWifiDirectConnection.deviceId == null) { "Wi-Fi transfer already active" }
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.NEARBY_WIFI_DEVICES
            else Manifest.permission.ACCESS_FINE_LOCATION
        check(context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) { "Wi-Fi permission is required" }
        checkNotNull(manager) { "Wi-Fi Direct is not available" }
        if (context.getSystemService(WifiManager::class.java)?.isWifiEnabled != true) throw HeyCyanWifiDisabledException()
        check(activeDevice() == deviceId) { "Selected glasses changed" }
        this@HeyCyanWifiDirectConnection.deviceId = deviceId
        selection = CompletableDeferred()
        freshIp.value = null
        group.value = null
        selectedPeer = null
        mutableState.value = WifiConnectionState.CONNECTING
        val opened = manager.initialize(context, Looper.getMainLooper()) {
            mutableState.value = WifiConnectionState.FAILED
            selection.completeExceptionally(IllegalStateException("Wi-Fi Direct channel disconnected"))
        }
        channel = opened
        val existing = withTimeout(5_000) { suspendCancellableCoroutine<WifiP2pGroup?> { continuation ->
            manager.requestGroupInfo(opened) { if (continuation.isActive) continuation.resume(it) }
        } }
        check(existing == null) { "Another Wi-Fi Direct connection is active; disconnect it before transferring" }
        ipJob = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            events.collect { event ->
                val reported = when (event) {
                    is HeyCyanEvent.WiFiIpReceived -> event.ip
                    is HeyCyanEvent.WifiDeviceIpReported -> event.ip
                    else -> null
                }
                if (activeDevice() == deviceId) {
                    if (reported != null) {
                        freshIp.value = reported
                        Log.i("HaloMedia", "P2P fresh device address received")
                    }
                    if (event is HeyCyanEvent.WifiTransferError) {
                        Log.i("HaloMedia", "P2P device transfer error code=${event.code}")
                    }
                }
            }
        }
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (channel !== opened) return
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> refreshPeers(opened, deviceId)
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> manager.requestGroupInfo(opened) { actual ->
                        if (channel === opened) {
                            group.value = actual
                            if (actual == null && mutableState.value == WifiConnectionState.CONNECTED) {
                                mutableState.value = WifiConnectionState.FAILED
                                Log.i("HaloMedia", "P2P connection lost")
                            }
                        }
                    }
                }
            }
        }.also { ContextCompat.registerReceiver(context, it, IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }, ContextCompat.RECEIVER_NOT_EXPORTED) }
        mutableSelection.value = HeyCyanWifiPeerSelection(deviceId)
        action { manager.discoverPeers(opened, it) }
        refreshPeers(opened, deviceId)
        Log.i("HaloMedia", "P2P discovery started before transfer command")
    }

    private fun refreshPeers(opened: WifiP2pManager.Channel, owner: String) {
        manager.requestPeers(opened) { list ->
            if (channel === opened && !selection.isCompleted && activeDevice() == owner) {
                mutableSelection.value = HeyCyanWifiPeerSelection(owner, list.deviceList.map {
                    HeyCyanWifiPeer(it.deviceAddress, it.deviceName.orEmpty().ifBlank { it.deviceAddress })
                }.distinctBy { it.address }.sortedBy { it.name })
                Log.i("HaloMedia", "P2P discovery peers=${list.deviceList.size}")
            }
        }
    }

    override suspend fun connect(credentials: HeyCyanWifiCredentials): Result<Unit> =
        Result.failure(UnsupportedOperationException("This transport uses Wi-Fi Direct"))

    override suspend fun connectDirect(deviceId: String): Result<Unit> = try {
        withTimeout(90_000) {
            val peer = selection.await()
            check(activeDevice() == deviceId && this@HeyCyanWifiDirectConnection.deviceId == deviceId) { "Selected glasses changed" }
            selectedPeer = peer
            val opened = checkNotNull(channel)
            withContext(Dispatchers.Main.immediate) {
                requestedConnection = true
                action { completion -> manager.connect(opened, WifiP2pConfig().apply {
                    deviceAddress = peer
                    wps.setup = WpsInfo.PBC
                }, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = completion.onSuccess()
                    override fun onFailure(reason: Int) {
                        // A rejected request owns no negotiation. In particular BUSY
                        // may belong to another app; do not cancel that app's request.
                        requestedConnection = false
                        completion.onFailure(reason)
                    }
                }) }
                manager.requestGroupInfo(opened) { if (channel === opened) group.value = it }
            }
            withTimeout(35_000) { group.first { it != null && ownsPeer(it, peer) } }
            check(activeDevice() == deviceId) { "Selected glasses changed" }
            mutableState.value = WifiConnectionState.CONNECTED
            Log.i("HaloMedia", "P2P selected peer connected")
        }
        Result.success(Unit)
    } catch (timeout: TimeoutCancellationException) {
        mutableState.value = WifiConnectionState.FAILED
        Result.failure(IllegalStateException("Wi-Fi Direct connection timed out", timeout))
    } catch (cancelled: CancellationException) { throw cancelled }
      catch (error: Exception) { mutableState.value = WifiConnectionState.FAILED; Result.failure(error) }
      finally {
          if (mutableState.value != WifiConnectionState.CONNECTED) cancelPendingConnection()
      }

    /** A coroutine timeout does not cancel Android's outstanding group negotiation. */
    private suspend fun cancelPendingConnection(): Unit = withContext(NonCancellable + Dispatchers.Main.immediate) {
        val opened = channel ?: return@withContext
        if (!requestedConnection) return@withContext
        val cancelled = runCatching { action { manager.cancelConnect(opened, it) } }.isSuccess
        // Group formation can finish just as the timeout fires. Query the platform,
        // and remove only a late group containing this session's explicitly chosen peer.
        val actual = runCatching {
            withTimeout(5_000) { suspendCancellableCoroutine<WifiP2pGroup?> { continuation ->
                manager.requestGroupInfo(opened) { if (continuation.isActive) continuation.resume(it) }
            } }
        }.getOrNull()
        if (channel === opened && actual?.let { current -> selectedPeer?.let { ownsPeer(current, it) } } == true) {
            runCatching { action { manager.removeGroup(opened, it) } }
                .onSuccess { group.value = null }
        }
        freshIp.value = null
        Log.i("HaloMedia", "P2P unsuccessful attempt cancelled=$cancelled before retry")
    }

    override suspend fun currentAddress(): InetAddress? = withTimeoutOrNull(15_000) {
        val ip = freshIp.filterNotNull().first()
        checkCurrentGroup()
        val remote = literalIpv4(ip)
        val iface = NetworkInterface.getByName(checkNotNull(group.value).`interface`)
            ?: error("Wi-Fi Direct interface unavailable")
        check(iface.inetAddresses.toList().none { it == remote }) { "Glasses reported the phone's address" }
        remote
    }

    fun openConnection(url: URL): HttpURLConnection {
        checkCurrentGroup()
        val ip = requireNotNull(freshIp.value) { "No fresh device-reported address" }
        require(url.protocol == "http" && url.host == ip && url.port in setOf(-1, 80)) { "Unexpected transfer endpoint" }
        val ifaceName = requireNotNull(group.value).`interface`
        val network = connectivity.allNetworks.firstOrNull { connectivity.getLinkProperties(it)?.interfaceName == ifaceName }
        if (network != null) return network.openConnection(url, Proxy.NO_PROXY) as HttpURLConnection
        // Some Android P2P groups have an interface but no ConnectivityManager Network.
        // Bind only transfer sockets to that observed interface's local IPv4 address.
        val local = NetworkInterface.getByName(ifaceName)?.inetAddresses?.toList()?.filterIsInstance<Inet4Address>()?.singleOrNull()
            ?: error("Wi-Fi Direct local address unavailable")
        val client = httpClient ?: okhttp3.OkHttpClient.Builder()
            .socketFactory(LocalAddressSocketFactory(local))
            .proxy(Proxy.NO_PROXY).followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .build().also { httpClient = it }
        return TransferHttpConnection(url, client)
    }

    private fun checkCurrentGroup() {
        check(deviceId != null && activeDevice() == deviceId && mutableState.value == WifiConnectionState.CONNECTED) { "Transfer connection is no longer current" }
        check(group.value?.let { actual -> selectedPeer?.let { ownsPeer(actual, it) } } == true) { "Selected Wi-Fi peer is no longer connected" }
    }

    override suspend fun disconnect() = withContext(NonCancellable + Dispatchers.Main.immediate) {
        val opened = channel
        channel = null
        mutableSelection.value = HeyCyanWifiPeerSelection()
        selection.cancel()
        ipJob?.cancelAndJoin(); ipJob = null
        receiver?.let { runCatching { context.unregisterReceiver(it) } }; receiver = null
        httpClient?.dispatcher?.cancelAll(); httpClient?.connectionPool?.evictAll(); httpClient = null
        if (opened != null) {
            runCatching { action { manager.stopPeerDiscovery(opened, it) } }
            if (requestedConnection) {
                runCatching { action { manager.cancelConnect(opened, it) } }
                // Never remove a group belonging to a different selected peer.
                if (group.value?.let { actual -> selectedPeer?.let { ownsPeer(actual, it) } } == true) {
                    runCatching { action { manager.removeGroup(opened, it) } }
                }
            }
            if (Build.VERSION.SDK_INT >= 27) opened.close()
        }
        requestedConnection = false; selectedPeer = null; deviceId = null
        group.value = null; freshIp.value = null
        mutableState.value = WifiConnectionState.DISCONNECTED
    }

    private suspend fun action(start: (WifiP2pManager.ActionListener) -> Unit) = withTimeout(5_000) {
        suspendCancellableCoroutine<Unit> { continuation ->
            start(object : WifiP2pManager.ActionListener {
                override fun onSuccess() { if (continuation.isActive) continuation.resume(Unit) }
                override fun onFailure(reason: Int) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("Wi-Fi Direct request failed ($reason)")) }
            })
        }
    }

    private fun ownsPeer(group: WifiP2pGroup, peer: String) =
        group.owner?.deviceAddress?.equals(peer, true) == true || group.clientList.any { it.deviceAddress.equals(peer, true) }

    private fun literalIpv4(value: String): InetAddress {
        val bytes = value.split('.').map { it.toIntOrNull()?.takeIf { octet -> octet in 0..255 } ?: error("Invalid device IPv4 address") }
        require(bytes.size == 4)
        val address = InetAddress.getByAddress(bytes.map { it.toByte() }.toByteArray())
        require(address.isSiteLocalAddress && !address.isLoopbackAddress) { "Device address is not local" }
        return address
    }
}
