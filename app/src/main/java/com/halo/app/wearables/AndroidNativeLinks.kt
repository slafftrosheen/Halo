package com.halo.app.wearables

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.halo.sdk.runtime.wearables.NativeByteLink
import com.halo.sdk.runtime.wearables.NativePacket
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private fun Context.nativeBluetoothAdapter(): BluetoothAdapter {
    if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
        throw SecurityException("Allow nearby-device access before connecting")
    }
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    return manager?.adapter?.takeIf { it.isEnabled } ?: throw IOException("Bluetooth is off or unavailable")
}

/** One-use GATT instance. No HeyCyan UUIDs, heuristics, reflection or shared callback slots. */
@SuppressLint("MissingPermission")
class EyeVueAndroidLink(context: Context, private val address: String) : NativeByteLink {
    private val context = context.applicationContext
    private val closed = AtomicBoolean(false)
    private val guard = Any()
    private var gatt: BluetoothGatt? = null
    private var command: BluetoothGattCharacteristic? = null
    @Volatile private var photoSubscribed = false
    override val availableChannels: Set<String> get() = if (photoSubscribed) setOf("command", "photo") else setOf("command")
    private val discovered = CompletableDeferred<BluetoothGatt>()
    private val negotiatedMtu = CompletableDeferred<Int>()
    private val messages = Channel<NativePacket>(256)
    override val incoming: Flow<NativePacket> = messages.receiveAsFlow()
    private val mutableConnected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
    private val writes = Mutex()
    private class Write(val kind: String, val characteristic: UUID, val result: CompletableDeferred<Int>)
    private val pending = AtomicReference<Write?>(null)

    companion object {
        private val SERVICE = UUID.fromString("0000aa12-0000-1000-8000-00805f9b34fb")
        private val COMMAND = UUID.fromString("0000aa13-0000-1000-8000-00805f9b34fb")
        private val NOTIFY = UUID.fromString("0000aa14-0000-1000-8000-00805f9b34fb")
        private val PHOTO = UUID.fromString("0000aa15-0000-1000-8000-00805f9b34fb")
        private val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    private fun current(value: BluetoothGatt): Boolean = synchronized(guard) {
        if (closed.get()) false
        else if (gatt == null) { gatt = value; true }
        else gatt === value
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, state: Int) {
            if (!current(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) { fail(IOException("EyeVue GATT failed (status $status)")); return }
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> if (!gatt.discoverServices()) fail(IOException("Service discovery did not start"))
                BluetoothProfile.STATE_DISCONNECTED -> fail(IOException("EyeVue disconnected"))
            }
        }
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (current(gatt) && status == BluetoothGatt.GATT_SUCCESS) negotiatedMtu.complete(mtu)
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!current(gatt)) return
            if (status == BluetoothGatt.GATT_SUCCESS) discovered.complete(gatt)
            else fail(IOException("EyeVue service discovery failed (status $status)"))
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!current(gatt) || descriptor.uuid != CCCD) return
            pending.get()?.takeIf { it.kind == "descriptor" && it.characteristic == descriptor.characteristic.uuid }
                ?.result?.complete(status)
        }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!current(gatt)) return
            pending.get()?.takeIf { it.kind == "command" && it.characteristic == characteristic.uuid }
                ?.result?.complete(status)
        }
        @Deprecated("Android pre-33 callback")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            accept(gatt, characteristic.uuid, characteristic.value ?: return)
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            accept(gatt, characteristic.uuid, value)
        }
    }

    private fun accept(gatt: BluetoothGatt, characteristic: UUID, bytes: ByteArray) {
        if (!current(gatt) || characteristic !in setOf(NOTIFY, PHOTO)) return
        val packet = NativePacket(bytes.copyOf(), if (characteristic == PHOTO) "photo" else "command")
        if (messages.trySend(packet).isFailure) fail(IOException("EyeVue receive queue overflow"))
    }

    override suspend fun open() {
        check(!closed.get()) { "Create a fresh GATT link for reconnect" }
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "Invalid Bluetooth address" }
        try {
            val device = context.nativeBluetoothAdapter().getRemoteDevice(address)
            val connectedGatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("Android did not create a GATT connection")
            if (!current(connectedGatt)) { connectedGatt.close(); throw IOException("Connection cancelled") }
            val readyGatt = withTimeout(30_000) { discovered.await() }
            val service = readyGatt.getService(SERVICE) ?: throw IOException("EyeVue service AA12 is missing")
            command = service.getCharacteristic(COMMAND)?.takeIf {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            } ?: throw IOException("EyeVue command endpoint AA13 is missing or not writable")
            val notify = service.getCharacteristic(NOTIFY) ?: throw IOException("EyeVue notify endpoint AA14 is missing")
            val photo = service.getCharacteristic(PHOTO)?.takeIf {
                it.getDescriptor(CCCD) != null && it.properties and
                    (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
            }
            val subscriptions = listOfNotNull(notify, photo)
            for (characteristic in subscriptions) {
                val flags = characteristic.properties
                val notification = flags and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                check(notification || flags and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) { "Endpoint cannot notify" }
                val descriptor = characteristic.getDescriptor(CCCD) ?: throw IOException("Notification CCCD is missing")
                check(readyGatt.setCharacteristicNotification(characteristic, true)) { "Could not enable notifications" }
                val value = if (notification) BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE else BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                writeAndWait("descriptor", characteristic.uuid) {
                    if (Build.VERSION.SDK_INT >= 33) readyGatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
                    else {
                        @Suppress("DEPRECATION")
                        descriptor.value = value
                        @Suppress("DEPRECATION")
                        readyGatt.writeDescriptor(descriptor)
                    }
                }
            }
            photoSubscribed = photo != null
            // Photo notifications benefit from a larger MTU; lack of negotiation
            // must not reject an otherwise valid default-MTU control channel.
            if (readyGatt.requestMtu(247)) withTimeoutOrNull(2_000) { negotiatedMtu.await() }
            check(!closed.get())
            mutableConnected.value = true
        } catch (error: Exception) { fail(error); throw error }
    }

    override suspend fun write(bytes: ByteArray) {
        check(mutableConnected.value && !closed.get()) { "EyeVue link is not ready" }
        // All currently exposed EyeVue commands fit the default ATT payload.
        require(bytes.size <= 20) { "EyeVue command needs negotiated MTU support" }
        val g = synchronized(guard) { gatt } ?: throw IOException("EyeVue disconnected")
        val c = command ?: throw IOException("EyeVue command endpoint missing")
        val type = if (c.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        try {
            writeAndWait("command", c.uuid) {
                if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(c, bytes.copyOf(), type) == BluetoothStatusCodes.SUCCESS
                else {
                    @Suppress("DEPRECATION")
                    c.writeType = type
                    @Suppress("DEPRECATION")
                    c.value = bytes.copyOf()
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(c)
                }
            }
        } catch (error: Exception) { fail(error); throw error }
    }

    private suspend fun writeAndWait(kind: String, uuid: UUID, start: () -> Boolean) = writes.withLock {
        val operation = Write(kind, uuid, CompletableDeferred())
        check(!closed.get() && pending.compareAndSet(null, operation))
        try {
            check(start()) { "Android rejected the GATT write" }
            val status = withTimeout(5_000) { operation.result.await() }
            check(status == BluetoothGatt.GATT_SUCCESS) { "GATT write failed (status $status)" }
        } finally { pending.compareAndSet(operation, null); operation.result.cancel() }
    }

    private fun fail(error: Exception) {
        if (!closed.compareAndSet(false, true)) return
        mutableConnected.value = false
        photoSubscribed = false
        discovered.completeExceptionally(error)
        pending.getAndSet(null)?.result?.completeExceptionally(error)
        val previous = synchronized(guard) { val old = gatt; gatt = null; command = null; old }
        try { previous?.disconnect() } catch (_: Exception) { }
        try { previous?.close() } catch (_: Exception) { }
        messages.close(error)
    }
    override suspend fun close() { fail(IOException("EyeVue link closed")) }
}

/** Classic RFCOMM, with user-authorized bonding and socket-close cancellation. */
@SuppressLint("MissingPermission")
class NativeRfcommLink(
    context: Context,
    private val address: String,
    private val allowBonding: Boolean,
    private val service: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"),
) : NativeByteLink {
    private val context = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val socket = AtomicReference<BluetoothSocket?>(null)
    private val shutdown = CompletableDeferred<Unit>()
    private val closed = AtomicBoolean(false)
    private val messages = Channel<NativePacket>(256)
    override val incoming: Flow<NativePacket> = messages.receiveAsFlow()
    private val mutableConnected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
    private val writes = Mutex()

    override suspend fun open() {
        require(BluetoothAdapter.checkBluetoothAddress(address)) { "Invalid Bluetooth address" }
        check(!closed.get())
        try {
            val adapter = context.nativeBluetoothAdapter()
            val device = adapter.getRemoteDevice(address)
            ensureBonded(device)
            try { adapter.cancelDiscovery() } catch (_: SecurityException) { }
            val pendingSocket = device.createRfcommSocketToServiceRecord(service)
            check(socket.compareAndSet(null, pendingSocket))
            withTimeout(20_000) { blocking { pendingSocket.connect() } }
            check(!closed.get() && pendingSocket.isConnected)
            mutableConnected.value = true
            scope.launch {
                try {
                    val buffer = ByteArray(4096)
                    while (isActive && !closed.get()) {
                        val length = pendingSocket.inputStream.read(buffer)
                        if (length < 0) throw IOException("RFCOMM stream ended")
                        if (length > 0) messages.send(NativePacket(buffer.copyOf(length)))
                    }
                } catch (error: Exception) { closeNow(error) }
            }
        } catch (error: Exception) { closeNow(error); throw error }
    }

    private suspend fun ensureBonded(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        check(allowBonding) { "Bluetooth bond was removed; explicitly pair this device again" }
        val result = CompletableDeferred<Int>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                @Suppress("DEPRECATION")
                val changed = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (!changed.address.equals(address, ignoreCase = true)) return
                val bond = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                if (bond == BluetoothDevice.BOND_BONDED || bond == BluetoothDevice.BOND_NONE) result.complete(bond)
            }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED), ContextCompat.RECEIVER_EXPORTED)
        try {
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> return
                BluetoothDevice.BOND_NONE -> check(device.createBond()) { "Android rejected the pairing request" }
            }
            check(withTimeout(40_000) {
                kotlinx.coroutines.selects.select<Int> {
                    result.onAwait { it }
                    shutdown.onAwait { throw IOException("Pairing connection closed") }
                }
            } == BluetoothDevice.BOND_BONDED) { "Bluetooth pairing was declined" }
        } finally { try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) { }; result.cancel() }
    }

    override suspend fun write(bytes: ByteArray) = writes.withLock {
        val active = socket.get() ?: throw IOException("RFCOMM is disconnected")
        check(mutableConnected.value && !closed.get())
        blocking { active.outputStream.write(bytes); active.outputStream.flush() }
    }

    private suspend fun blocking(action: () -> Unit): Unit = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { closeNow(IOException("RFCOMM operation cancelled")) }
        val task = scope.launch {
            try { action(); if (continuation.isActive) continuation.resume(Unit) }
            catch (error: Exception) {
                closeNow(error)
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
        task.invokeOnCompletion { cause -> if (cause != null && continuation.isActive) continuation.cancel(cause) }
    }

    private fun closeNow(error: Exception) {
        if (!closed.compareAndSet(false, true)) return
        mutableConnected.value = false
        shutdown.complete(Unit)
        try { socket.getAndSet(null)?.close() } catch (_: IOException) { }
        messages.close(error)
        scope.cancel()
    }
    override suspend fun close() { closeNow(IOException("RFCOMM closed")) }
}
