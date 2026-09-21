package com.halo.app.capture

import android.content.Context
import android.util.Log
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.wifi.*
import com.halo.drivers.heycyan.media.repository.*
import com.halo.drivers.heycyan.media.sync.*
import com.halo.sdk.runtime.*
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.net.InetAddress
import java.util.UUID

data class WearableCaptureStatus(val busy: Boolean = false, val message: String = "Ready", val received: Int = 0, val lastSync: Long? = null)
data class PendingCaptureOperation(
    val operationId: String,
    val deviceId: String,
    val requestedAt: Long
)

/** Existing verified transport composition. Receipt, rather than BLE write, establishes success. */
class WearableCaptureCoordinator(private val context: Context, private val driver: RealHeyCyanDriver,
    private val captures: LocalCaptureStore, private val events: EventBus, private val scope: CoroutineScope) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(WearableCaptureStatus())
    val state = _state.asStateFlow()
    private val imported = context.getSharedPreferences("received_wearable_media", Context.MODE_PRIVATE)
    private val pendingCaptureStore = context.getSharedPreferences("pending_wearable_capture", Context.MODE_PRIVATE)
    private var pendingCaptureOperation: PendingCaptureOperation? = loadPendingCapture()
    private val connector = HeyCyanWifiConnector(context)
    private val wifiState = MutableStateFlow(WifiConnectionState.DISCONNECTED)
    private val wifi = object : HeyCyanWifiConnection {
        override val state: StateFlow<WifiConnectionState> = wifiState
        override suspend fun connect(credentials: HeyCyanWifiCredentials): Result<Unit> {
            wifiState.value = WifiConnectionState.CONNECTING
            return if (connector.connect(credentials.ssid, credentials.password)) {
                wifiState.value = WifiConnectionState.CONNECTED; Result.success(Unit)
            } else { wifiState.value = WifiConnectionState.FAILED; Result.failure(IllegalStateException("Wi-Fi connection unavailable or permission denied")) }
        }
        override suspend fun disconnect() { connector.disconnect(); wifiState.value = WifiConnectionState.DISCONNECTED }
        override suspend fun currentAddress(): InetAddress? = HeyCyanIpDiscovery().discover()?.let(InetAddress::getByName)
    }
    private val pendingSync = MutableStateFlow(false)

    fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            driver.heyCyanEvents.collect { event ->
                if (event == HeyCyanEvent.AiImageRecognition) {
                    // Do not block BC59 event delivery while HTTP/media work runs.
                    scope.launch {
                        capture(requestPhoto = false)
                        pendingSync.value = true
                    }
                }
            }
        }
        scope.launch {
            driver.deviceState.mapNotNull { it.wifiCredentials }.collect {
                if (pendingSync.value) {
                    pendingSync.value = false
                    sync()
                }
            }
        }
    }
    suspend fun capture(requestPhoto: Boolean = true) {
        if (!mutex.tryLock()) return
        val id = UUID.randomUUID().toString()
        val device = driver.activeDeviceId
        try {
            requireNotNull(device) { "No connected HeyCyan device" }
            _state.value = _state.value.copy(busy = true, message = "Requested � waiting for media")
            events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Wearable capture", stage = "Requested", detail = "Waiting for actual media", sourceDeviceId = device))
            if (requestPhoto) {
                driver.capabilityController.execute(HeyCyanOperation.TakePhoto).getOrThrow()
                events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Wearable capture", stage = "Dispatched", detail = "Photo command sent; image receipt pending", sourceDeviceId = device))
            }
            driver.capabilityController.execute(HeyCyanOperation.GetMediaInventory).getOrThrow()
            Log.i("HaloMedia", "MEDIA_INVENTORY_REQUESTED operation=$id device=$device")
            pendingCaptureOperation = PendingCaptureOperation(id, device, System.currentTimeMillis()).also(::persistPendingCapture)
            _state.value = _state.value.copy(message = "Capture requested. Sync media to receive the image.")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _state.value = _state.value.copy(message = error.message ?: "Capture request failed")
            events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Wearable capture", stage = "Failed", detail = _state.value.message, sourceDeviceId = device))
        } finally { _state.value = _state.value.copy(busy = false); mutex.unlock() }
    }
    suspend fun sync() {
        if (!mutex.tryLock()) {
            pendingSync.value = true
            return
        }
        val device = driver.activeDeviceId
        val id = pendingCaptureOperation
            ?.takeIf { it.deviceId == device }
            ?.operationId
            ?: UUID.randomUUID().toString()
        try {
            requireNotNull(device) { "No connected HeyCyan device" }
            val credentialsProvider = DeviceStateWifiCredentialProvider(driver.deviceState)
            _state.value = _state.value.copy(busy = true, message = "Awaiting transfer credentials")
            events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Media synchronization", stage = "Pending", detail = "Awaiting transfer credentials", sourceDeviceId = device))

            var api: HeyCyanMediaApi? = null
            val transfer = HeyCyanTransferCoordinator(driver.capabilityController, credentialsProvider, wifi,
                HeyCyanMediaApiFactory { address -> DefaultHeyCyanMediaApi(address).also { api = it } })
            transfer.synchronizeManifest(device).getOrElse { error ->
                if (transfer.state.value is HeyCyanTransferState.WaitingForCredentials) {
                    pendingSync.value = true
                    _state.value = _state.value.copy(busy = false, message = "Awaiting transfer credentials")
                    return
                }
                throw error
            }
            _state.value = _state.value.copy(busy = true, message = "Transfer ready")
            events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Media synchronization", stage = "Transfer ready", detail = "Credentials acquired", sourceDeviceId = device))
            val activeApi = checkNotNull(api)
            val deviceKey = UUID.nameUUIDFromBytes(device.toByteArray()).toString()
            val repository = FileHeyCyanMediaRepository(File(context.filesDir, "captures/transfer/$deviceKey"))
            val sync = HeyCyanMediaSynchronizer(activeApi, repository,
                HeyCyanMediaDiffer(DefaultHeyCyanMediaIdentityResolver()), StreamingMediaDownloadStrategy(activeApi))
            val progress = scope.launch {
                sync.state.collect { value ->
                    if (value is HeyCyanMediaSyncState.Downloading) _state.value = _state.value.copy(
                        message = "Receiving item ${value.index}/${value.total} � ${value.progress.bytesReceived} bytes")
                }
            }
            val result = try { sync.synchronize(MediaSyncRequest(mediaTypes = setOf(HeyCyanMediaType.PHOTO))).getOrThrow() }
                finally { progress.cancelAndJoin() }
            var received = 0
            for (item in repository.list().filter { it.mediaType == HeyCyanMediaType.PHOTO }) {
                val key = "$deviceKey:${item.identity}:${item.sha256}"
                if (!imported.getBoolean(key, false)) {
                    // Imported images have their own identities; an inventory cannot prove
                    // which physical button event produced an older, unsynchronized file.
                    captures.receive(File(item.localPath), device, item.identity)
                    imported.edit().putBoolean(key, true).commit()
                    received++
                }
            }
            check(result.failed == 0) { "${result.failed} media downloads failed; received images remain in Inbox" }
            _state.value = WearableCaptureStatus(message = "$received images received in Inbox", received = received, lastSync = System.currentTimeMillis())
            events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Media synchronization", stage = "Completed", detail = _state.value.message, sourceDeviceId = device))
            if (pendingCaptureOperation?.operationId == id) clearPendingCapture()
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _state.value = _state.value.copy(message = error.message ?: "Media transfer failed")
            events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Media synchronization", stage = "Failed", detail = _state.value.message, sourceDeviceId = device))
        } finally { withContext(NonCancellable) { wifi.disconnect() }; _state.value = _state.value.copy(busy = false); mutex.unlock() }
    }

    private fun loadPendingCapture(): PendingCaptureOperation? {
        val operationId = pendingCaptureStore.getString("operationId", null) ?: return null
        val deviceId = pendingCaptureStore.getString("deviceId", null) ?: return null
        val requestedAt = pendingCaptureStore.getLong("requestedAt", 0L)
        return PendingCaptureOperation(operationId, deviceId, requestedAt)
    }

    private fun persistPendingCapture(operation: PendingCaptureOperation) {
        pendingCaptureStore.edit()
            .putString("operationId", operation.operationId)
            .putString("deviceId", operation.deviceId)
            .putLong("requestedAt", operation.requestedAt)
            .apply()
    }

    private fun clearPendingCapture() {
        pendingCaptureOperation = null
        pendingCaptureStore.edit().clear().apply()
    }
}
