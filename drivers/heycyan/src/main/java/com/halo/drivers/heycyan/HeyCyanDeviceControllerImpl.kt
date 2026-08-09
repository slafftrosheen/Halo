package com.halo.drivers.heycyan

import com.halo.drivers.heycyan.ble.HeyCyanGattClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Complete implementation of HeyCyanDeviceController.
 *
 * Uses HeyCyanCommandDispatcher for send-command-and-await-response pattern,
 * HeyCyanEventStream for continuous event monitoring, and maintains aggregate
 * device state in a StateFlow.
 *
 * Key changes from the previous broken implementation:
 * - waitForEvent() replaced with HeyCyanEventStream.awaitEvent()
 * - Commands serialized through HeyCyanCommandDispatcher
 * - Continuous battery polling
 * - Auto-query on connection initialization
 * - Mode mutual exclusion enforcement
 */
class HeyCyanDeviceControllerImpl(
    private val gattClient: HeyCyanGattClient,
    private val dispatcher: HeyCyanCommandDispatcher,
    private val eventStream: HeyCyanEventStream,
    private val batteryPollIntervalMillis: Long = DEFAULT_BATTERY_POLL_INTERVAL
) : HeyCyanDeviceController {

    companion object {
        private const val DEFAULT_BATTERY_POLL_INTERVAL = 60_000L // 60 seconds
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var batteryPollJob: Job? = null
    private var eventMonitorJob: Job? = null

    private val _connectionState = MutableStateFlow(HeyCyanConnectionState.UNBOUND)
    override val connectionState: StateFlow<HeyCyanConnectionState> = _connectionState.asStateFlow()

    private val _deviceState = MutableStateFlow(HeyCyanDeviceState.INITIAL)
    override val deviceState: StateFlow<HeyCyanDeviceState> = _deviceState.asStateFlow()

    private val _batteryUpdates = MutableStateFlow<BatteryState?>(null)
    override val batteryUpdates: StateFlow<BatteryState?> = _batteryUpdates.asStateFlow()

    private val _currentMode = MutableStateFlow<HeyCyanMode?>(null)
    override val currentMode: StateFlow<HeyCyanMode?> = _currentMode.asStateFlow()

    private val _unknownEvents = MutableSharedFlow<HeyCyanBleEvent.Unknown>(extraBufferCapacity = 64)
    override val unknownEvents: Flow<HeyCyanBleEvent.Unknown> = _unknownEvents.asSharedFlow()

    // ── Lifecycle ────────────────────────────────────────────────────

    override suspend fun initializeDevice(): Result<HeyCyanDeviceState> = runCatching {
        _connectionState.value = HeyCyanConnectionState.READY

        // Start continuous event monitoring
        startEventMonitor()

        // Query all device information
        val version = runCatching {
            val event = dispatcher.queryVersion().getOrThrow()
            DeviceVersion(
                hardwareVersion = event.hardwareVersion,
                firmwareVersion = event.firmwareVersion,
                wifiHardwareVersion = event.wifiHardwareVersion,
                wifiFirmwareVersion = event.wifiFirmwareVersion
            )
        }.getOrNull()

        val battery = runCatching {
            val event = dispatcher.queryBattery().getOrThrow()
            BatteryState(level = event.level, isCharging = event.isCharging)
        }.getOrNull()

        val mediaCounts = runCatching {
            val event = dispatcher.queryMediaCounts().getOrThrow()
            MediaCounts(
                photos = event.photos,
                videos = event.videos,
                audios = event.audios,
                mediaType = event.mediaType
            )
        }.getOrNull()

        // Sync datetime (fire-and-forget)
        runCatching { dispatcher.sendSetDateTime() }

        // Update state
        val now = System.currentTimeMillis()
        val state = HeyCyanDeviceState(
            connection = HeyCyanConnectionState.READY,
            battery = battery,
            version = version,
            macAddress = null, // MAC response format TBD
            mediaCounts = mediaCounts,
            currentMode = null,
            lastUpdated = now
        )
        _deviceState.value = state
        battery?.let { _batteryUpdates.value = it }

        // Start periodic battery polling
        startBatteryPolling()

        state
    }

    override suspend fun reconnect(address: String): Result<Unit> = runCatching {
        _connectionState.value = HeyCyanConnectionState.RECONNECTING
        gattClient.connect(address)
        gattClient.awaitReady()
        initializeDevice()
        Unit
    }

    // ── Device Information ───────────────────────────────────────────

    override suspend fun getVersion(): Result<DeviceVersion> = runCatching {
        val event = dispatcher.queryVersion().getOrThrow()
        val version = DeviceVersion(
            hardwareVersion = event.hardwareVersion,
            firmwareVersion = event.firmwareVersion,
            wifiHardwareVersion = event.wifiHardwareVersion,
            wifiFirmwareVersion = event.wifiFirmwareVersion
        )
        updateState { copy(version = version, lastUpdated = System.currentTimeMillis()) }
        version
    }

    override suspend fun getBattery(): Result<BatteryState> = runCatching {
        val event = dispatcher.queryBattery().getOrThrow()
        val battery = BatteryState(level = event.level, isCharging = event.isCharging)
        updateState { copy(battery = battery, lastUpdated = System.currentTimeMillis()) }
        _batteryUpdates.value = battery
        battery
    }

    override suspend fun getMediaCounts(): Result<MediaCounts> = runCatching {
        val event = dispatcher.queryMediaCounts().getOrThrow()
        val counts = MediaCounts(
            photos = event.photos,
            videos = event.videos,
            audios = event.audios,
            mediaType = event.mediaType
        )
        updateState { copy(mediaCounts = counts, lastUpdated = System.currentTimeMillis()) }
        counts
    }

    override suspend fun getMacAddress(): Result<String> = runCatching {
        val event = dispatcher.queryMacAddress().getOrThrow()
        // MAC response format is TBD — attempt to parse from unknown event
        val mac = if (event is HeyCyanBleEvent.Unknown && event.rawPayload.size > 1) {
            String(event.rawPayload.copyOfRange(1, event.rawPayload.size), Charsets.UTF_8)
        } else {
            throw IllegalStateException("Unexpected response format for MAC query")
        }
        updateState { copy(macAddress = mac, lastUpdated = System.currentTimeMillis()) }
        mac
    }

    override suspend fun setDateTime(): Result<Unit> = runCatching {
        dispatcher.sendSetDateTime().getOrThrow()
    }

    // ── Device Operations ────────────────────────────────────────────

    override suspend fun setMode(mode: HeyCyanMode): Result<HeyCyanMode> = runCatching {
        val event = dispatcher.sendModeCommand(mode).getOrThrow()
        _currentMode.value = event.mode
        updateState { copy(currentMode = event.mode, lastUpdated = System.currentTimeMillis()) }
        event.mode
    }

    override suspend fun takePhoto(): Result<Unit> = runCatching {
        enforceNoActiveRecording()
        dispatcher.sendModeCommand(HeyCyanMode.PHOTO).getOrThrow()
        _currentMode.value = HeyCyanMode.PHOTO
        updateState { copy(currentMode = HeyCyanMode.PHOTO, lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun startVideoRecording(): Result<Unit> = runCatching {
        enforceNoActiveRecording()
        dispatcher.sendModeCommand(HeyCyanMode.VIDEO).getOrThrow()
        _currentMode.value = HeyCyanMode.VIDEO
        updateState { copy(currentMode = HeyCyanMode.VIDEO, lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun stopVideoRecording(): Result<Unit> = runCatching {
        dispatcher.sendModeCommand(HeyCyanMode.VIDEO_STOP).getOrThrow()
        _currentMode.value = HeyCyanMode.VIDEO_STOP
        updateState { copy(currentMode = null, lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun startAudioRecording(): Result<Unit> = runCatching {
        enforceNoActiveRecording()
        dispatcher.sendModeCommand(HeyCyanMode.AUDIO).getOrThrow()
        _currentMode.value = HeyCyanMode.AUDIO
        updateState { copy(currentMode = HeyCyanMode.AUDIO, lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun stopAudioRecording(): Result<Unit> = runCatching {
        dispatcher.sendModeCommand(HeyCyanMode.AUDIO_STOP).getOrThrow()
        _currentMode.value = HeyCyanMode.AUDIO_STOP
        updateState { copy(currentMode = null, lastUpdated = System.currentTimeMillis()) }
    }

    override suspend fun triggerAIPhoto(): Result<Unit> = Result.failure(
        UnsupportedOperationException(
            "AI Photo is BLOCKED_BY_UNKNOWN_PROTOCOL: trigger command (mode 0x06) exists " +
                "but chunk reassembly and image output format are not fully understood. " +
                "See SDK_API_PARITY.md §8."
        )
    )

    // ── WiFi Transfer ────────────────────────────────────────────────

    override suspend fun enableTransferMode(): Result<WiFiCredentials> = runCatching {
        val event = dispatcher.sendTransferMode().getOrThrow()
        _currentMode.value = HeyCyanMode.TRANSFER
        updateState { copy(currentMode = HeyCyanMode.TRANSFER, lastUpdated = System.currentTimeMillis()) }
        // CyanBridge finding: glasses may return wrong password, force "123456789"
        WiFiCredentials(
            ssid = event.ssid,
            password = event.password.ifBlank { "123456789" }
        )
    }

    // ── Internal ─────────────────────────────────────────────────────

    private fun startEventMonitor() {
        if (eventMonitorJob != null) return
        eventMonitorJob = scope.launch {
            eventStream.events.collect { event ->
                when (event) {
                    is HeyCyanBleEvent.Battery -> {
                        val battery = BatteryState(level = event.level, isCharging = event.isCharging)
                        _batteryUpdates.value = battery
                        updateState { copy(battery = battery, lastUpdated = event.timestamp) }
                    }
                    is HeyCyanBleEvent.ModeChanged -> {
                        _currentMode.value = event.mode
                        updateState { copy(currentMode = event.mode, lastUpdated = event.timestamp) }
                    }
                    is HeyCyanBleEvent.MediaCounts -> {
                        val counts = MediaCounts(
                            photos = event.photos,
                            videos = event.videos,
                            audios = event.audios,
                            mediaType = event.mediaType
                        )
                        updateState { copy(mediaCounts = counts, lastUpdated = event.timestamp) }
                    }
                    is HeyCyanBleEvent.Version -> {
                        val version = DeviceVersion(
                            hardwareVersion = event.hardwareVersion,
                            firmwareVersion = event.firmwareVersion,
                            wifiHardwareVersion = event.wifiHardwareVersion,
                            wifiFirmwareVersion = event.wifiFirmwareVersion
                        )
                        updateState { copy(version = version, lastUpdated = event.timestamp) }
                    }
                    is HeyCyanBleEvent.Unknown -> {
                        _unknownEvents.tryEmit(event)
                    }
                    else -> {
                        // WiFiCredentials, AIImageChunk, Error — handled by direct queries
                    }
                }
            }
        }
    }

    private fun startBatteryPolling() {
        if (batteryPollJob != null) return
        batteryPollJob = scope.launch {
            while (true) {
                delay(batteryPollIntervalMillis)
                if (_connectionState.value == HeyCyanConnectionState.READY) {
                    runCatching { getBattery() }
                }
            }
        }
    }

    /**
     * Enforces the SDK constraint: cannot record video and audio simultaneously.
     */
    private fun enforceNoActiveRecording() {
        val mode = _currentMode.value
        check(mode != HeyCyanMode.VIDEO) {
            "Cannot start new capture while video is recording. Stop video first."
        }
        check(mode != HeyCyanMode.AUDIO) {
            "Cannot start new capture while audio is recording. Stop audio first."
        }
    }

    private fun updateState(transform: HeyCyanDeviceState.() -> HeyCyanDeviceState) {
        _deviceState.value = _deviceState.value.transform()
    }

    /**
     * Stops all background jobs. Called during disconnect.
     */
    fun shutdown() {
        batteryPollJob?.cancel()
        batteryPollJob = null
        eventMonitorJob?.cancel()
        eventMonitorJob = null
        _connectionState.value = HeyCyanConnectionState.DISCONNECTED
    }
}