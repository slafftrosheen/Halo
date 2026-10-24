package com.halo.app.embodiment

import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.embodiment.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID

data class SavedDeviceSnapshot(val record: DeviceRecord, val sourceVersion: String, val sessionState: SessionState?)
data class DevicePreferenceSnapshot(val thumbnailQuality: Int, val sourceVersion: String)
data class SavedDeviceReceipt(val deviceId: String, val sourceVersion: String?, val saved: Boolean,
    val requested: Boolean, val autoConnect: Boolean? = null)
enum class SavedDeviceCommand { RETRY, CONNECT, DISCONNECT, AUTO_CONNECT, FORGET }

/** Shared UI/assistant owner over the existing registry, session manager and reconnect coordinator.
 * No discovery, pairing, protocol bytes or new session construction is admitted here. */
class SavedDeviceActions(
    private val registry: DeviceRegistry,
    private val sessions: DeviceSessionManager,
    private val storage: Storage? = null,
    private val retry: (suspend (String) -> Boolean)? = null,
) {
    private val mutex = Mutex()
    private val lifetime = UUID.randomUUID().toString()
    private var mutation = 0L
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()
    private val quality = MutableStateFlow(storage?.getString(QUALITY_KEY)?.toIntOrNull()?.coerceIn(0, 5) ?: 3)
    val thumbnailQuality: StateFlow<Int> = quality.asStateFlow()

    suspend fun list(): List<SavedDeviceSnapshot> = mutex.withLock {
        registry.getSavedDevices().sortedBy { it.identity.deviceId }.map(::snapshot)
    }
    suspend fun get(deviceId: String): SavedDeviceSnapshot? = mutex.withLock {
        registry.getDevice(deviceId)?.let(::snapshot)
    }
    suspend fun preferences(): DevicePreferenceSnapshot = mutex.withLock { refreshQuality(); preferenceSnapshot() }
    suspend fun setThumbnailQuality(value: Int, expectedVersion: String? = null): DevicePreferenceSnapshot? = mutex.withLock {
        require(value in 0..5)
        refreshQuality()
        if (expectedVersion != null && expectedVersion != preferenceSnapshot().sourceVersion) return@withLock null
        // Missing persistence is not a saved preference and must not receive a success receipt.
        val targetStorage = storage ?: return@withLock null
        changed()
        try {
            targetStorage.putString(QUALITY_KEY, value.toString())
            check(targetStorage.getString(QUALITY_KEY)?.toIntOrNull() == value) { "Preference persistence outcome unknown" }
            quality.value = value
            preferenceSnapshot()
        } finally { _revision.value += 1 }
    }

    /** UI supplies no token because its explicit gesture selects the current record.
     * Assistant adapters must supply the exact read token after durable approval. */
    suspend fun apply(deviceId: String, command: SavedDeviceCommand, enabled: Boolean? = null,
        expectedVersion: String? = null): SavedDeviceReceipt? = mutex.withLock {
        val record = registry.getDevice(deviceId) ?: return@withLock null
        val before = snapshot(record)
        if (expectedVersion != null && expectedVersion != before.sourceVersion) return@withLock null
        if (command == SavedDeviceCommand.AUTO_CONNECT && enabled == null) return@withLock null
        if (command != SavedDeviceCommand.AUTO_CONNECT && enabled != null) return@withLock null
        val session = sessions.getSession(deviceId)
        if (session != null && (session.identity.deviceId != record.identity.deviceId || session.identity.deviceType != record.identity.deviceType))
            return@withLock null
        // All source tokens are consumed before invoking an external host, including failed/uncertain requests.
        try { when (command) {
            SavedDeviceCommand.RETRY -> {
                if (!canRetry(record, before.sessionState) || retry == null) return@withLock null
                changed()
                val requested = retry.invoke(deviceId)
                SavedDeviceReceipt(deviceId, registry.getDevice(deviceId)?.let(::snapshot)?.sourceVersion,
                    saved = registry.getDevice(deviceId) != null, requested = requested, autoConnect = record.autoConnect)
            }
            SavedDeviceCommand.CONNECT, SavedDeviceCommand.DISCONNECT -> {
                if (session == null) return@withLock null
                if (session.identity.deviceId != record.identity.deviceId || session.identity.deviceType != record.identity.deviceType)
                    return@withLock null
                changed()
                if (command == SavedDeviceCommand.CONNECT) session.connect() else session.disconnect()
                SavedDeviceReceipt(deviceId, registry.getDevice(deviceId)?.let(::snapshot)?.sourceVersion,
                    saved = registry.getDevice(deviceId) != null, requested = true, autoConnect = record.autoConnect)
            }
            SavedDeviceCommand.AUTO_CONNECT -> {
                changed()
                registry.saveDevice(record.copy(autoConnect = requireNotNull(enabled)))
                val saved = checkNotNull(registry.getDevice(deviceId)) { "Saved record changed after preference write" }
                check(saved.autoConnect == enabled) { "Preference readback changed" }
                val requested = if (enabled) retry?.invoke(deviceId) == true else false
                SavedDeviceReceipt(deviceId, registry.getDevice(deviceId)?.let(::snapshot)?.sourceVersion,
                    saved = true, requested = requested, autoConnect = saved.autoConnect)
            }
            SavedDeviceCommand.FORGET -> {
                changed()
                // Keep the existing UI sequence: stop its session, then remove exactly its saved record.
                session?.disconnect()
                registry.removeDevice(deviceId)
                check(registry.getDevice(deviceId) == null) { "Removal readback unknown" }
                SavedDeviceReceipt(deviceId, null, saved = false, requested = session != null)
            }
        } } finally { _revision.value += 1 }
    }

    private fun snapshot(record: DeviceRecord): SavedDeviceSnapshot {
        val state = sessions.getSession(record.identity.deviceId)?.state?.value
        return SavedDeviceSnapshot(record, version("saved-device", "$record|$state"), state)
    }
    private fun preferenceSnapshot() = DevicePreferenceSnapshot(quality.value, version("device-preferences", quality.value.toString()))
    private fun refreshQuality() {
        val saved = storage?.getString(QUALITY_KEY)?.toIntOrNull()
        if (saved != null && saved in 0..5) quality.value = saved
    }
    private fun version(prefix: String, value: String): String = "$prefix:" + MessageDigest.getInstance("SHA-256")
        .digest("$lifetime|$mutation|$value".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private fun changed() { mutation++ }

    companion object {
        private const val QUALITY_KEY = "heycyan_ai_thumbnail_quality"
        fun canRetry(record: DeviceRecord, state: SessionState?): Boolean = record.autoConnect &&
            record.descriptor.kind in setOf(DeviceKind.HEYCYAN_GLASSES, DeviceKind.BUDDYBOT) &&
            state !in setOf(SessionState.READY, SessionState.CONNECTING, SessionState.CONNECTED,
                SessionState.HANDSHAKE, SessionState.CAPABILITY_SYNC, SessionState.STATE_SYNC, SessionState.DISCONNECTING)
    }
}
