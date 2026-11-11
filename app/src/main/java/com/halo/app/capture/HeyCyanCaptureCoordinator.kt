package com.halo.app.capture

import android.content.Context
import android.util.Log
import com.halo.drivers.heycyan.RealHeyCyanDriver
import com.halo.drivers.heycyan.protocol.HeyCyanEvent
import com.halo.drivers.heycyan.thumbnail.HeyCyanCapturedThumbnail
import com.halo.drivers.heycyan.wifi.*
import com.halo.drivers.heycyan.media.repository.*
import com.halo.drivers.heycyan.media.sync.*
import com.halo.sdk.host.ObservationSource
import com.halo.sdk.runtime.*
import com.halo.sdk.runtime.embodiment.heycyan.HeyCyanOperation
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import java.io.File
import java.net.InetAddress
import java.util.UUID

data class WearableCaptureStatus(
    val busy: Boolean = false, val message: String = "Ready", val received: Int = 0, val lastSync: Long? = null,
    val deviceId: String? = null, val failed: Boolean = false, val waitingForConnection: Boolean = false,
    val itemIndex: Int? = null, val itemTotal: Int? = null,
    val captureRequested: Boolean = false,
)
/** Existing verified transport composition. Receipt, rather than BLE write, establishes success. */
class HeyCyanCaptureCoordinator(private val context: Context, private val driver: RealHeyCyanDriver,
    private val captures: LocalCaptureStore, private val events: EventBus, private val scope: CoroutineScope,
    private val preloadLocalModel: () -> Unit = {},
    private val mediaImporter: com.halo.sdk.runtime.wearables.media.DeviceMediaImporter,
    private val canManageFiles: () -> Boolean) {
    private val mutex = Mutex()
    private val hardwareThumbnailMutex = Mutex()
    private val _state = MutableStateFlow(WearableCaptureStatus())
    val state = _state.asStateFlow()
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
        override suspend fun currentAddress(): InetAddress? =
            HeyCyanIpDiscovery(connectionFactory = connector::openHttpConnection)
                .discover(driver.deviceState.value.wifiIp)
                ?.let(InetAddress::getByName)
    }
    private val thumbnailQuality: Int
        get() = context.getSharedPreferences("heycyan_settings", Context.MODE_PRIVATE)
            .getInt("ai_thumbnail_quality", 3)
            .coerceIn(0, 5)

    fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            driver.heyCyanEvents.collect { event ->
                if (event == HeyCyanEvent.AiImageRecognition) {
                    // Photo-ready is the gate for the low-latency BLE path.
                    if (!driver.thumbnailCaptureCoordinator.isAwaitingPhotoReady && !hardwareThumbnailMutex.isLocked) scope.launch {
                        receiveReadyAiThumbnail()
                    }
                }
            }
        }

    }

    suspend fun captureAiThumbnail(quality: Int = thumbnailQuality, publishForInference: Boolean = false, review: Boolean = true): Result<HeyCyanCapturedThumbnail> {
        if(review && !publishForInference && com.halo.app.di.ManualDependencyGraph.contextualCapture.state.value != null)
            return Result.failure(IllegalStateException("Finish the current capture before requesting another"))
        if (publishForInference) preloadLocalModel()
        val device = driver.activeDeviceId
            ?: return Result.failure(IllegalStateException("No connected HeyCyan device"))
        val operationId = UUID.randomUUID().toString()
        events.publish(ActionReceipt(operationId = operationId, category = "Vision", title = "AI thumbnail", stage = "Requested", detail = "Capturing a fresh glasses thumbnail", sourceDeviceId = device))
        val result = driver.thumbnailCaptureCoordinator.capture(quality)
        result.onSuccess { thumbnail -> if (publishForInference) publishThumbnail(operationId, device, thumbnail)
            else if(review) com.halo.app.di.ManualDependencyGraph.contextualCapture.receive(
                com.halo.sdk.runtime.multimodal.MediaAssetContent(thumbnail.bytes, "image/jpeg"), CaptureSource.MANAGED_WEARABLE_CAMERA,
                "Wearable camera", operationId, device, CaptureQuality.AI_THUMBNAIL, captureTimestampMs=thumbnail.photoReadyAtMs, trigger=if(thumbnail.commandAtMs == null) "HARDWARE_TRIGGER" else "SOFTWARE_REQUEST") }
            .onFailure { error ->
                events.publish(ActionReceipt(operationId = operationId, category = "Vision", title = "AI thumbnail", stage = "Failed", detail = error.message ?: "Thumbnail capture failed", sourceDeviceId = device))
            }
        return result
    }

    private suspend fun receiveReadyAiThumbnail() {
        if (!hardwareThumbnailMutex.tryLock()) return
        try {
        val device = driver.activeDeviceId ?: return
        val operationId = UUID.randomUUID().toString()
        events.publish(ActionReceipt(operationId = operationId, category = "Vision", title = "AI thumbnail", stage = "DeviceReady", detail = "Glasses reported a fresh image", sourceDeviceId = device))
        val firstAttempt = driver.thumbnailCaptureCoordinator.receivePhotoReadyThumbnail(quality = thumbnailQuality)
        val result = if (firstAttempt.isFailure && driver.activeDeviceId == device) {
            driver.thumbnailCaptureCoordinator.receivePhotoReadyThumbnail(quality = thumbnailQuality)
        } else firstAttempt
        result
            .onSuccess { thumbnail -> com.halo.app.di.ManualDependencyGraph.contextualCapture.receive(
                com.halo.sdk.runtime.multimodal.MediaAssetContent(thumbnail.bytes, "image/jpeg"), CaptureSource.MANAGED_WEARABLE_CAMERA,
                "Wearable camera", operationId, device, CaptureQuality.AI_THUMBNAIL, captureTimestampMs=thumbnail.photoReadyAtMs, trigger=if(thumbnail.commandAtMs == null) "HARDWARE_TRIGGER" else "SOFTWARE_REQUEST") }
            .onFailure { error ->
                events.publish(ActionReceipt(operationId = operationId, category = "Vision", title = "AI thumbnail", stage = "Failed", detail = error.message ?: "Thumbnail transfer failed", sourceDeviceId = device))
            }
        } finally {
            hardwareThumbnailMutex.unlock()
        }
    }

    private suspend fun publishThumbnail(
        operationId: String,
        deviceId: String,
        thumbnail: HeyCyanCapturedThumbnail,
    ) {
        val captureMetadata = mutableMapOf<String, Any>(
            "captureKind" to "heycyan_ai_thumbnail",
            "quality" to thumbnail.quality,
            "photoReadyAtMs" to thumbnail.photoReadyAtMs,
            "completionAtMs" to thumbnail.completedAtMs,
            "captureOrigin" to if (thumbnail.commandAtMs == null) "hardware_trigger" else "software_request",
        )
        thumbnail.commandAtMs?.let { captureMetadata["commandAtMs"] = it }
        thumbnail.firstChunkAtMs?.let { captureMetadata["firstChunkAtMs"] = it }
        events.publish(ActionReceipt(operationId = operationId, category = "Vision", title = "AI thumbnail", stage = "Validated", detail = "${thumbnail.width}×${thumbnail.height} · ${thumbnail.bytes.size} bytes", sourceDeviceId = deviceId))
        events.publish(
            ObservationUpdated(
                observation = Observation(
                    modality = Modality.IMAGE,
                    source = Source.CAMERA,
                    payload = ImageObservationPayload(mediaRef = com.halo.app.di.ManualDependencyGraph.mediaAssetStore.ingest(
                        com.halo.sdk.runtime.multimodal.MediaAssetContent(thumbnail.bytes, "image/jpeg"),
                        com.halo.sdk.runtime.multimodal.MediaProvenance.HEYCYAN_CAMERA, operationId, deviceId,
                    )),
                    origin = ObservationOrigin(ObservationSource.HeyCyanGlasses, deviceId),
                    sourceDeviceId = deviceId,
                    freshness = Freshness.FRESH,
                    timestamp = thumbnail.completedAtMs,
                    metadata = captureMetadata,
                )
            )
        )
        events.publish(ActionReceipt(operationId = operationId, category = "Vision", title = "AI thumbnail", stage = "Completed", detail = "Fresh image is ready for inference", sourceDeviceId = deviceId))
    }
    suspend fun capture(requestPhoto: Boolean = true) {
        if (!mutex.tryLock()) return
        val id = UUID.randomUUID().toString()
        val device = driver.activeDeviceId
        try {
            requireNotNull(device) { "No connected HeyCyan device" }
            _state.value = (_state.value.takeIf { it.deviceId == device } ?: WearableCaptureStatus()).copy(
                busy = true, message = "Requested · waiting for media", deviceId = device, failed = false,
                waitingForConnection = false, itemIndex = null, itemTotal = null, captureRequested = true)
            events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Wearable capture", stage = "Requested", detail = "Waiting for actual media", sourceDeviceId = device))
            if (requestPhoto) {
                driver.capabilityController.execute(HeyCyanOperation.TakePhoto).getOrThrow()
                events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Wearable capture", stage = "Dispatched", detail = "Photo command sent; image receipt pending", sourceDeviceId = device))
            }
            driver.capabilityController.execute(HeyCyanOperation.GetMediaInventory).getOrThrow()
            Log.i("HaloMedia", "MEDIA_INVENTORY_REQUESTED operation=$id device=$device")
            _state.value = _state.value.copy(message = "Capture requested. Browse device files and select the image to copy into Library.")
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            _state.value = _state.value.copy(message = error.message ?: "Capture request failed", failed = true, deviceId = device)
            events.publish(ActionReceipt(operationId = id, category = "Vision", title = "Wearable capture", stage = "Failed", detail = _state.value.message, sourceDeviceId = device))
        } finally { _state.value = _state.value.copy(busy = false); mutex.unlock() }
    }
    val files = com.halo.sdk.runtime.wearables.media.DeviceMediaManager(
        object : com.halo.sdk.runtime.wearables.media.DeviceMediaTransport {
            override suspend fun <T> withSession(deviceId: String, allowed: () -> Boolean,
                block: suspend (com.halo.sdk.runtime.wearables.media.DeviceMediaSession) -> T): T =
                withFileSession(deviceId, allowed, block)
        }, mediaImporter, canManageFiles)

    /** Compatibility action: first lists files; deliberate selections are imported by the file panel. */
    suspend fun sync() { driver.activeDeviceId?.let { files.refresh(it) } }

    private suspend fun <T> withFileSession(device: String, allowed: () -> Boolean,
        block: suspend (com.halo.sdk.runtime.wearables.media.DeviceMediaSession) -> T): T {
        check(mutex.tryLock()) { "Glasses capture or transfer already running" }
        var transfer: HeyCyanTransferCoordinator? = null
        var staging: File? = null
        fun current() = driver.activeDeviceId == device && allowed() && canManageFiles()
        try {
            val saved = com.halo.app.di.ManualDependencyGraph.deviceRegistry.getDevice(device)
            check(saved?.descriptor?.kind == com.halo.sdk.runtime.embodiment.DeviceKind.HEYCYAN_GLASSES)
            check(current() && driver.deviceState.value.connection == com.halo.drivers.heycyan.protocol.HeyCyanConnectionState.READY)
            _state.value = WearableCaptureStatus(busy = true, message = "Opening device files", deviceId = device)
            var api: HeyCyanMediaApi? = null
            val coordinator = HeyCyanTransferCoordinator(driver.capabilityController,
                DeviceStateWifiCredentialProvider(driver.deviceState), wifi,
                HeyCyanMediaApiFactory { address -> DefaultHeyCyanMediaApi(address,
                    connectionFactory = { url -> check(current()); connector.openConnection(url) }).also { api = it } },
                driver.resourceArbiter, deviceIsCurrent = { id -> id == device && driver.activeDeviceId == device })
            transfer = coordinator
            val manifest = coordinator.synchronizeManifest(device).getOrThrow()
            check(current())
            val activeApi = requireNotNull(api)
            // Library owns completed bytes. This operation cache is disposable and must
            // not retain a second hidden copy when users manage their Library files.
            val stageRoot = File(context.cacheDir, "device-media-transfers")
            withContext(Dispatchers.IO) {
                stageRoot.listFiles()?.filter { it.isDirectory && runCatching { UUID.fromString(it.name) }.isSuccess }
                    ?.forEach { it.deleteRecursively() } // Orphans from a previous process; this host owns the mutex.
            }
            val directory = File(stageRoot, UUID.randomUUID().toString()).also { staging = it }
            val repository = FileHeyCyanMediaRepository(directory)
            val entries = manifest.items.map { item -> com.halo.sdk.runtime.wearables.media.DeviceMediaEntry(
                item.id, item.filename, item.toMediaType().name, item.sizeBytes, item.createdAt) }
            val result = block(object : com.halo.sdk.runtime.wearables.media.DeviceMediaSession {
                override val files = entries
                override suspend fun download(file: com.halo.sdk.runtime.wearables.media.DeviceMediaEntry,
                    progress: (Long, Long?) -> Unit): com.halo.sdk.runtime.wearables.media.DeviceMediaDownload {
                    check(current())
                    val remote = manifest.items.single { it.id == file.id && it.filename == file.name }
                    // Explicit selection fetches current bytes even when the device reuses a filename/size.
                    StreamingMediaDownloadStrategy(activeApi).download(remote, repository) {
                        check(current()); progress(it.bytesReceived, it.totalBytes)
                    }.getOrThrow()
                    check(current())
                    val saved = requireNotNull(repository.find(DefaultHeyCyanMediaIdentityResolver().identity(remote)))
                    return com.halo.sdk.runtime.wearables.media.DeviceMediaDownload(saved.localPath, requireNotNull(saved.sha256), saved.sizeBytes)
                }
            })
            return result
        } finally {
            try { withContext(NonCancellable) {
                try { withTimeout(8_000) { transfer?.closeSession()?.getOrThrow() } } finally { wifi.disconnect() }
            } } finally {
                try { withContext(NonCancellable + Dispatchers.IO) { staging?.deleteRecursively() } }
                finally { _state.value = _state.value.copy(busy = false, message = "Device file session closed"); mutex.unlock() }
            }
        }
    }

}
