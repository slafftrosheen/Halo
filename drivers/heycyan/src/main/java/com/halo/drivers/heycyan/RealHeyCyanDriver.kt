package com.halo.drivers.heycyan

import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import com.halo.drivers.heycyan.ble.HeyCyanBleScanner
import com.halo.drivers.heycyan.ble.HeyCyanGattClient
import com.halo.drivers.heycyan.codec.LoggingPacketTracer
import com.halo.drivers.heycyan.codec.CaptureSessionRecorder
import com.halo.drivers.heycyan.codec.CaptureMetadata
import com.halo.drivers.heycyan.codec.CompositePacketTracer
import com.halo.drivers.heycyan.codec.JsonCaptureSessionStore
import com.halo.libraries.logging.ConsoleLogger
import com.halo.sdk.driver.DeviceEvent
import com.halo.sdk.driver.Driver
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * Real HeyCyan transport driver.
 *
 * PR-011 records complete BLE discovery and transport sessions. Protocol decoding
 * and command serialization still wait for evidence from replayable captures.
 */
class RealHeyCyanDriver(
    private val context: Context
) : Driver {

    companion object {
        private const val SCAN_TIMEOUT_MILLIS = 30_000L
    }

    private val captureRecorder = CaptureSessionRecorder(
        JsonCaptureSessionStore(File(context.filesDir, "captures/heycyan"))
    )
    private val tracer = CompositePacketTracer(
        LoggingPacketTracer(ConsoleLogger()),
        captureRecorder
    )
    private val scanner = HeyCyanBleScanner(context)
    private val gattClient = HeyCyanGattClient(context, tracer)

    private val _events = MutableSharedFlow<DeviceEvent>(extraBufferCapacity = 64)
    override val events: Flow<DeviceEvent> = _events.asSharedFlow()
    override val state: StateFlow<DriverState> = gattClient.state

    override suspend fun initialize() = Unit

    override suspend fun connect() {
        captureRecorder.start(
            scenario = "runtime",
            metadata = captureMetadata()
        )
        gattClient.markScanning()

        try {
            val advertisement = withTimeout(SCAN_TIMEOUT_MILLIS) {
                scanner.scanForDevice().first()
            }
            captureRecorder.recordAdvertisement(
                com.halo.drivers.heycyan.codec.CaptureAdvertisement(
                    timestamp = System.currentTimeMillis(),
                    address = advertisement.address,
                    name = advertisement.name,
                    rssi = advertisement.rssi,
                    serviceUuids = advertisement.serviceUuids,
                    manufacturerData = advertisement.manufacturerData
                )
            )
            gattClient.connect(advertisement.address)
            captureRecorder.setDevice(advertisement.address, advertisement.name)
            gattClient.awaitReady()
        } catch (error: CancellationException) {
            captureRecorder.stop()
            throw error
        } catch (error: Throwable) {
            gattClient.fail(error)
            captureRecorder.stop()
            throw error
        }
    }

    override suspend fun disconnect() {
        gattClient.disconnect()
        gattClient.awaitDisconnected()
        captureRecorder.stop()
    }

    override suspend fun shutdown() {
        disconnect()
    }

    override suspend fun emit(event: DeviceEvent) {
        _events.emit(event)
    }

    private fun captureMetadata(): CaptureMetadata {
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val bluetoothAdapter = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.name
        }.getOrNull()

        return CaptureMetadata(
            haloVersion = packageInfo?.versionName,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            phoneModel = listOf(Build.MANUFACTURER, Build.MODEL)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { null },
            bluetoothAdapter = bluetoothAdapter
        )
    }
}
