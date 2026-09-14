package com.halo.app.embodiment

import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.embodiment.BuddyBotSession
import com.halo.sdk.runtime.embodiment.DeviceRecord
import com.halo.sdk.runtime.embodiment.DeviceSessionManager
import com.halo.sdk.runtime.embodiment.WebSocketEmbodimentTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BuddyBotTelemetry(
    val motorsArmed: Boolean = false,
    val rangeMm: Int? = null,
    val rangeValid: Boolean = false,
    val obstacleDetected: Boolean = false,
    val forwardMotionBlocked: Boolean = false,
    val safetyState: String? = null,
    val safetyFault: String? = null,
    val driveModeName: String? = null,
    val actionName: String? = null,
    val actionRunning: Boolean = false,
    val moodName: String? = null,
    val personaName: String? = null,
    val controllerPresent: Boolean = false,
    val pairingAvailable: Boolean = false,
    val wifiClientCount: Int = 0,
    val batteryPercent: Int? = null,
    val uptimeMs: Long = 0L,
    val revision: Long = 0L,
    val lastUpdateMs: Long = 0L
)

/**
 * Host controller for physical BuddyBot embodiment.
 * Manages WebSocket connection, device registration, pairing, and incoming telemetry.
 *
 * Construction is intentionally side-effect free. Device registration is performed
 * explicitly by the startup/connect path so creating a UI ViewModel cannot perform
 * storage access or transport allocation on the Compose main thread.
 */
class BuddyBotHost(
    private val sessionManager: DeviceSessionManager,
    private val eventBus: EventBus,
    private val storage: Storage,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val deviceId: String = "buddybot_1"
) {
    companion object {
        const val DEFAULT_BUDDYBOT_URL = "ws://192.168.4.1/ws"
        const val STORAGE_KEY_URL = "buddybot_ws_url"
    }

    private val _telemetry = MutableStateFlow(BuddyBotTelemetry())
    val telemetry: StateFlow<BuddyBotTelemetry> = _telemetry.asStateFlow()

    private var currentTransport: WebSocketEmbodimentTransport? = null
    var session: BuddyBotSession? = null
        private set

    fun getTargetUrl(): String {
        return storage.getString(STORAGE_KEY_URL, DEFAULT_BUDDYBOT_URL) ?: DEFAULT_BUDDYBOT_URL
    }

    fun setTargetUrl(url: String) {
        storage.putString(STORAGE_KEY_URL, url)
        scope.launch(Dispatchers.IO) {
            ensureDeviceRegistered(recreate = true)
        }
    }

    fun ensureDeviceRegistered(recreate: Boolean = false) {
        if (!recreate && sessionManager.getSession(deviceId) != null) {
            session = sessionManager.getSession(deviceId) as? BuddyBotSession
            return
        }

        val url = getTargetUrl()
        val identity = DeviceIdentity(deviceId, "BuddyBot", "BuddyBot")
        val transport = WebSocketEmbodimentTransport(url)
        currentTransport = transport

        val newSession = BuddyBotSession(
            identity = identity,
            transport = transport,
            eventBus = eventBus
        )
        session = newSession

        transport.onMessageReceived { message ->
            handleIncomingMessage(message)
        }

        sessionManager.registerSession(newSession)

        scope.launch(Dispatchers.IO) {
            val existing = sessionManager.registry.getDevice(deviceId)
            if (existing == null) {
                sessionManager.registry.saveDevice(
                    DeviceRecord(
                        identity = identity,
                        autoConnect = false,
                        firmwareVersion = "v0.3.0",
                        lastSeenMs = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    private fun handleIncomingMessage(message: String) {
        if (message.contains("\"type\":\"telemetry\"") || message.contains("\"type\": \"telemetry\"")) {
            try {
                val motorsArmed = message.contains("\"motorsArmed\":true") || message.contains("\"motorsArmed\": true")
                val obstacleDetected = message.contains("\"obstacleDetected\":true") || message.contains("\"obstacleDetected\": true")
                val rangeValid = message.contains("\"rangeValid\":true") || message.contains("\"rangeValid\": true")
                val forwardMotionBlocked = message.contains("\"forwardMotionBlocked\":true") || message.contains("\"forwardMotionBlocked\": true")
                val actionRunning = message.contains("\"actionRunning\":true") || message.contains("\"actionRunning\": true")
                val controllerPresent = message.contains("\"controllerPresent\":true") || message.contains("\"controllerPresent\": true")
                val pairingAvailable = message.contains("\"pairingAvailable\":true") || message.contains("\"pairingAvailable\": true")

                val rangeMatch = Regex("\"rangeMm\"\\s*:\\s*(\\d+)").find(message)
                val safetyStateMatch = Regex("\"safetyState\"\\s*:\\s*\"([^\"]+)\"").find(message)
                val safetyFaultMatch = Regex("\"safetyFault\"\\s*:\\s*\"([^\"]+)\"").find(message)
                val driveModeMatch = Regex("\"driveModeName\"\\s*:\\s*\"([^\"]+)\"").find(message)
                val actionMatch = Regex("\"actionName\"\\s*:\\s*\"([^\"]+)\"").find(message)
                val moodMatch = Regex("\"moodName\"\\s*:\\s*\"([^\"]+)\"").find(message)
                val personaMatch = Regex("\"personaName\"\\s*:\\s*\"([^\"]+)\"").find(message)
                val batteryMatch = Regex("\"batteryPercent\"\\s*:\\s*(\\d+)").find(message)
                val wifiClientsMatch = Regex("\"wifiClientCount\"\\s*:\\s*(\\d+)").find(message)
                val uptimeMatch = Regex("\"uptimeMs\"\\s*:\\s*(\\d+)").find(message)
                val revisionMatch = Regex("\"revision\"\\s*:\\s*(\\d+)").find(message)

                val rangeMm = rangeMatch?.groupValues?.get(1)?.toIntOrNull()
                val safetyState = safetyStateMatch?.groupValues?.get(1)
                val safetyFault = safetyFaultMatch?.groupValues?.get(1)
                val driveMode = driveModeMatch?.groupValues?.get(1)
                val action = actionMatch?.groupValues?.get(1)
                val mood = moodMatch?.groupValues?.get(1)
                val persona = personaMatch?.groupValues?.get(1)
                val battery = batteryMatch?.groupValues?.get(1)?.toIntOrNull()
                val wifiClients = wifiClientsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val uptime = uptimeMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L
                val revision = revisionMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                val updated = BuddyBotTelemetry(
                    motorsArmed = motorsArmed,
                    rangeMm = rangeMm,
                    rangeValid = rangeValid,
                    obstacleDetected = obstacleDetected,
                    forwardMotionBlocked = forwardMotionBlocked,
                    safetyState = safetyState,
                    safetyFault = safetyFault,
                    driveModeName = driveMode,
                    actionName = action,
                    actionRunning = actionRunning,
                    moodName = mood,
                    personaName = persona,
                    controllerPresent = controllerPresent,
                    pairingAvailable = pairingAvailable,
                    wifiClientCount = wifiClients,
                    batteryPercent = battery,
                    uptimeMs = uptime,
                    revision = revision,
                    lastUpdateMs = System.currentTimeMillis()
                )
                _telemetry.value = updated

                scope.launch(Dispatchers.IO) {
                    sessionManager.registry.getDevice(deviceId)?.let { record ->
                        sessionManager.registry.saveDevice(record.copy(lastSeenMs = System.currentTimeMillis()))
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun connect() {
        ensureDeviceRegistered()
        session?.connect()
    }

    suspend fun disconnect() {
        session?.disconnect()
    }

    suspend fun pair(code: String) {
        session?.pair(code)
    }
}
