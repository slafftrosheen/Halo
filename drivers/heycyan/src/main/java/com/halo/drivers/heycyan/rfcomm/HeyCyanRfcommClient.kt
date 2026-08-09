package com.halo.drivers.heycyan.rfcomm

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import com.halo.drivers.heycyan.codec.PacketDirection
import com.halo.drivers.heycyan.codec.PacketTracer
import com.halo.sdk.driver.DriverState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.io.InputStream

/**
 * Raw RFCOMM transport for physical HeyCyan discovery.
 *
 * The channel UUIDs are transport candidates from the external research
 * reference. This class records bytes only; it does not decode group/opcode,
 * send commands, or claim a protocol meaning for any payload.
 */
class HeyCyanRfcommClient(
    private val context: Context,
    private val tracer: PacketTracer,
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MILLIS
) {
    private val operationMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val readJobs = mutableListOf<Job>()
    private val sockets = linkedMapOf<String, BluetoothSocket>()
    private val _state = MutableStateFlow<DriverState>(DriverState.Disconnected)
    private val _connectedChannels = MutableStateFlow<List<String>>(emptyList())

    val state: StateFlow<DriverState> = _state.asStateFlow()
    val connectedChannels: StateFlow<List<String>> = _connectedChannels.asStateFlow()

    @SuppressLint("MissingPermission")
    suspend fun connect(address: String): Result<List<String>> {
        require(address.isNotBlank()) { "Bluetooth address must not be blank" }
        if (!hasConnectPermission()) {
            return Result.failure(SecurityException("BLUETOOTH_CONNECT permission is required"))
        }

        return try {
            operationMutex.withLock {
                disconnectLocked()
                _state.value = DriverState.Connecting

                val adapter = bluetoothAdapter()
                    ?: throw IOException("BluetoothAdapter is not available")
                val device = runCatching { adapter.getRemoteDevice(address) }
                    .getOrElse { throw IOException("Invalid Bluetooth address: $address", it) }

                runCatching { adapter.cancelDiscovery() }
                val opened = mutableListOf<String>()

                for (channel in HeyCyanRfcommChannel.CANDIDATES) {
                    try {
                        val socket = connectSocket(device, channel)
                        sockets[channel.name] = socket
                        opened += channel.name
                        tracer.onRfcommStateChanged(channel.name, connected = true)
                        if (channel.name == HeyCyanRfcommChannel.PRIMARY.name) {
                            _state.value = DriverState.Connected
                        }
                        startReader(channel.name, socket.inputStream)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Throwable) {
                        if (channel.required) {
                            throw IOException(
                                "Required RFCOMM channel '${channel.name}' failed: ${error.message}",
                                error
                            )
                        }
                        // Auxiliary channels are evidence opportunities, not a
                        // reason to report the primary transport as successful.
                    }
                }

                if (opened.isEmpty()) {
                    throw IOException("No RFCOMM channel connected for $address")
                }

                _connectedChannels.value = opened.toList()
                _state.value = DriverState.Ready
                opened.toList()
            }.let { Result.success(it) }
        } catch (cancelled: CancellationException) {
            disconnect()
            throw cancelled
        } catch (error: Throwable) {
            disconnect()
            _state.value = DriverState.Error(error)
            Result.failure(error)
        }
    }

    suspend fun disconnect() {
        operationMutex.withLock {
            if (_state.value != DriverState.Disconnected) {
                _state.value = DriverState.Disconnecting
            }
            disconnectLocked()
            _state.value = DriverState.Disconnected
        }
    }

    fun shutdown() {
        readJobs.forEach(Job::cancel)
        readJobs.clear()
        sockets.values.forEach { socket -> runCatching { socket.close() } }
        sockets.clear()
        _connectedChannels.value = emptyList()
        _state.value = DriverState.Disconnected
        scope.cancel()
    }

    private fun bluetoothAdapter(): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun hasConnectPermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S ||
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private suspend fun connectSocket(
        device: android.bluetooth.BluetoothDevice,
        channel: HeyCyanRfcommChannel
    ): BluetoothSocket = withContext(Dispatchers.IO) {
        val socket = device.createRfcommSocketToServiceRecord(channel.uuid)
        try {
            withTimeout(connectTimeoutMillis) { socket.connect() }
            socket
        } catch (cancelled: CancellationException) {
            runCatching { socket.close() }
            throw cancelled
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw IOException("RFCOMM ${channel.name} connect failed", error)
        }
    }

    private fun startReader(channel: String, input: InputStream) {
        readJobs += scope.launch {
            val buffer = ByteArray(READ_BUFFER_SIZE)
            try {
                while (isActive) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count == 0) continue
                    tracer.onRfcomm(
                        channel = channel,
                        direction = PacketDirection.RX,
                        bytes = buffer.copyOf(count)
                    )
                }
            } catch (_: IOException) {
                // Disconnect and remote-close are both normal during capture.
            } finally {
                runCatching { input.close() }
                if (channel == HeyCyanRfcommChannel.PRIMARY.name && _state.value == DriverState.Ready) {
                    _state.value = DriverState.Disconnected
                }
            }
        }
    }

    private fun disconnectLocked() {
        readJobs.forEach(Job::cancel)
        readJobs.clear()
        sockets.keys.forEach { channel ->
            tracer.onRfcommStateChanged(channel, connected = false)
        }
        sockets.values.forEach { socket -> runCatching { socket.close() } }
        sockets.clear()
        _connectedChannels.value = emptyList()
    }

    private companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MILLIS = 8_000L
        const val READ_BUFFER_SIZE = 4 * 1024
    }
}
