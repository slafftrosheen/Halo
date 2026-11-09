package com.halo.app.wearables

import android.content.Context
import com.halo.drivers.heycyan.ble.BleAdvertisement
import com.halo.sdk.runtime.DeviceIdentity
import com.halo.sdk.runtime.EventBus
import com.halo.sdk.runtime.embodiment.*
import com.halo.sdk.runtime.wearables.*
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Constructs vendor transports; saved identity and lifecycle remain in Halo's existing owners. */
class NativeWearableHost(
    context: Context,
    private val registry: DeviceRegistry,
    private val sessions: DeviceSessionManager,
    private val events: EventBus,
) {
    private val context = context.applicationContext
    private val registration = Mutex()
    companion object {
        fun supports(kind: DeviceKind): Boolean = kind in setOf(DeviceKind.EYEVUE_GLASSES, DeviceKind.TUNEBUDS_GLASSES)

        /** AB Mate advertises an obfuscated Classic address, not necessarily its BLE MAC. */
        fun transportAddress(kind: DeviceKind, advertisement: BleAdvertisement): String {
            if (kind != DeviceKind.TUNEBUDS_GLASSES) return advertisement.address.uppercase(Locale.ROOT)
            val vendor = setOf(0x475a, 0x455a, 0x535a, 0x4d5a)
            for ((company, raw) in advertisement.manufacturerData) {
                if (company.toIntOrNull() !in vendor) continue
                val hex = raw.filterNot(Char::isWhitespace)
                if (hex.length < 18 || hex.length % 2 != 0 || hex.any { it.digitToIntOrNull(16) == null }) continue
                val bytes = hex.chunked(2).map { it.toInt(16) }
                return (3..8).joinToString(":") { "%02X".format(Locale.ROOT, bytes[it] xor 0xad) }
            }
            // This is the explicitly selected endpoint, never an unrelated bonded device.
            return advertisement.address.uppercase(Locale.ROOT)
        }
    }

    suspend fun ensure(record: DeviceRecord): NativeWearableSession = registration.withLock {
        require(supports(record.descriptor.kind)) { "This device requires a different native adapter" }
        withContext(Dispatchers.Main.immediate) {
            val existing = sessions.getSession(record.identity.deviceId)
            if (existing != null) {
                check(existing is NativeWearableSession && existing.identity.deviceType == record.identity.deviceType) {
                    "Saved identity belongs to a different session family"
                }
                return@withContext existing
            }
            val bondPermission = AtomicBoolean(false)
            val family = if (record.descriptor.kind == DeviceKind.EYEVUE_GLASSES) NativeFamily.EYEVUE else NativeFamily.TUNEBUDS
            val driver = NativeWearableDriver(record.identity.deviceId, family, {
                if (family == NativeFamily.EYEVUE) EyeVueAndroidLink(context, record.identity.deviceId)
                else NativeRfcommLink(context, record.identity.deviceId, bondPermission.getAndSet(false))
            })
            NativeWearableSession(record.identity, driver, events) { bondPermission.set(true) }
                .also(sessions::registerSession)
        }
    }

    suspend fun pair(kind: DeviceKind, advertisement: BleAdvertisement,
        onState: (NativeSnapshot) -> Unit = {}): DeviceRecord = coroutineScope {
        require(supports(kind))
        val address = transportAddress(kind, advertisement)
        val existing = registry.getDevice(address)
        check(existing == null || existing.descriptor.kind == kind) { "Saved endpoint belongs to another device family" }
        val label = advertisement.name?.trim()?.take(48)?.takeIf { it.isNotBlank() && it.none(Char::isISOControl) } ?: kind.label
        val record = existing ?: DeviceRecord(DeviceIdentity(address, kind.name, label))
        val session = ensure(record)
        session.authorizeExplicitConnection()
        val watcher = launch(start = CoroutineStart.UNDISPATCHED) { session.driver.state.collect(onState) }
        try {
            session.connect()
            check(session.state.value == SessionState.READY)
            val saved = record.copy(lastSeenMs = System.currentTimeMillis(), firmwareVersion = session.driver.state.value.firmware)
            registry.saveDevice(saved)
            sessions.refreshSavedDevices()
            saved
        } catch (error: Exception) {
            if (existing == null) {
                withContext(NonCancellable) {
                    session.dispose()
                    withContext(Dispatchers.Main.immediate) { sessions.unregisterSession(address) }
                }
            }
            throw error
        } finally { watcher.cancel() }
    }

    suspend fun retry(record: DeviceRecord, explicit: Boolean = false): Boolean {
        if (!record.autoConnect || !supports(record.descriptor.kind)) return false
        val session = ensure(record)
        if (explicit) session.authorizeExplicitConnection()
        else if (session.autoReconnectSuppressed) return false
        return sessions.autoConnectDevice(record.identity.deviceId)
    }
}
