package com.halo.app.wearables

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import com.halo.sdk.runtime.wearables.NativeByteLink
import com.halo.sdk.runtime.wearables.NativePacket
import com.halo.sdk.runtime.wearables.frame.FrameStatusProtocol
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Selected Frame endpoint only. One-use link, serialized ATT operations, no scanning.
 * Unbonded background reconnect fails before opening GATT. Pairing stays with Android.
 */
@SuppressLint("MissingPermission")
class BrilliantFrameAndroidLink(context: Context, private val address: String, private val allowBonding: Boolean) : NativeByteLink {
    private val context = context.applicationContext
    private val closed = AtomicBoolean(false)
    private val guard = Any()
    private var gatt: BluetoothGatt? = null
    private var tx: BluetoothGattCharacteristic? = null
    private val connection = CompletableDeferred<Unit>()
    private val discovered = CompletableDeferred<Unit>()
    private val mtuResult = CompletableDeferred<Int>()
    private val shutdown = CompletableDeferred<Unit>()
    private val messages = Channel<NativePacket>(64)
    override val incoming: Flow<NativePacket> = messages.receiveAsFlow()
    private val mutableConnected = MutableStateFlow(false)
    override val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()
    @Volatile private var payloadLimit = 20
    override val maxWriteBytes: Int get() = payloadLimit
    private val writes = Mutex()
    private class Write(val descriptor: Boolean, val uuid: UUID, val result: CompletableDeferred<Int>)
    private val pending = AtomicReference<Write?>(null)
    private val serviceId = UUID.fromString(FrameStatusProtocol.SERVICE)
    private val txId = UUID.fromString(FrameStatusProtocol.TX)
    private val rxId = UUID.fromString(FrameStatusProtocol.RX)
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun current(value: BluetoothGatt): Boolean = synchronized(guard) {
        if (closed.get()) false
        else if (gatt == null) { gatt = value; true }
        else gatt === value
    }
    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, state: Int) {
            if (!current(gatt)) return
            if (status != BluetoothGatt.GATT_SUCCESS) { fail(IOException("Frame GATT connection failed ($status)")); return }
            if (state == BluetoothProfile.STATE_CONNECTED) connection.complete(Unit)
            else if (state == BluetoothProfile.STATE_DISCONNECTED) fail(IOException("Frame disconnected"))
        }
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (!current(gatt)) return
            if (status == BluetoothGatt.GATT_SUCCESS) discovered.complete(Unit)
            else fail(IOException("Frame service discovery failed ($status)"))
        }
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (!current(gatt)) return
            if (status == BluetoothGatt.GATT_SUCCESS && mtu in 23..517) mtuResult.complete(mtu)
            else fail(IOException("Frame MTU negotiation failed"))
        }
        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (!current(gatt) || descriptor.uuid != cccd) return
            pending.get()?.takeIf { it.descriptor && it.uuid == descriptor.characteristic.uuid }?.result?.complete(status)
        }
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (!current(gatt)) return
            pending.get()?.takeIf { !it.descriptor && it.uuid == characteristic.uuid }?.result?.complete(status)
        }
        @Deprecated("Android pre-33 callback")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            @Suppress("DEPRECATION")
            receive(gatt, characteristic.uuid, characteristic.value ?: return)
        }
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            receive(gatt, characteristic.uuid, value)
        }
    }
    private fun receive(gatt: BluetoothGatt, uuid: UUID, bytes: ByteArray) {
        if (!current(gatt) || uuid != rxId) return
        if (bytes.size > 512 || messages.trySend(NativePacket(bytes.copyOf())).isFailure)
            fail(IOException("Frame notification bounds exceeded"))
    }
    override suspend fun open() {
        check(!closed.get()) { "Create a new Frame link to reconnect" }
        require(BluetoothAdapter.checkBluetoothAddress(address))
        try {
            if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                throw SecurityException("Allow nearby-device access before connecting")
            val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
                ?.takeIf { it.isEnabled } ?: throw IOException("Bluetooth is off or unavailable")
            val device = adapter.getRemoteDevice(address)
            check(device.bondState == BluetoothDevice.BOND_BONDED || allowBonding) { "Explicitly pair Frame before reconnecting" }
            val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
                ?: throw IOException("Android did not create Frame GATT")
            if (!current(g)) { g.close(); throw IOException("Frame connection cancelled") }
            withTimeout(20_000) { connection.await() }
            ensureBonded(device)
            check(g.discoverServices()) { "Frame service discovery did not start" }
            withTimeout(15_000) { discovered.await() }
            val service = g.getService(serviceId) ?: throw IOException("Frame service is missing")
            tx = service.getCharacteristic(txId)?.takeIf {
                it.properties and (BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
            } ?: throw IOException("Frame TX endpoint is missing or not writable")
            val rx = service.getCharacteristic(rxId)?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
                ?: throw IOException("Frame RX notification endpoint is missing")
            check(g.requestMtu(512)) { "Frame MTU negotiation did not start" }
            payloadLimit = (withTimeout(5_000) { mtuResult.await() } - 3).coerceAtMost(509)
            val needed = FrameStatusProtocol.Query.entries.maxOf { FrameStatusProtocol.command(it, "0".repeat(32)).size }
            check(payloadLimit >= needed) { "Negotiated Frame MTU cannot carry status queries" }
            val descriptor = rx.getDescriptor(cccd) ?: throw IOException("Frame RX CCCD is missing")
            check(g.setCharacteristicNotification(rx, true)) { "Frame RX subscription failed" }
            writeAndWait(true, rx.uuid) {
                if (Build.VERSION.SDK_INT >= 33) g.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    @Suppress("DEPRECATION")
                    g.writeDescriptor(descriptor)
                }
            }
            check(!closed.get() && device.bondState == BluetoothDevice.BOND_BONDED)
            mutableConnected.value = true
        } catch (error: Exception) { fail(error); throw error }
    }
    private suspend fun ensureBonded(device: BluetoothDevice) {
        if (device.bondState == BluetoothDevice.BOND_BONDED) return
        check(allowBonding) { "Frame bond changed; pair explicitly" }
        val result = CompletableDeferred<Int>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                @Suppress("DEPRECATION")
                val changed = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (!changed.address.equals(address, ignoreCase = true)) return
                val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                if (state == BluetoothDevice.BOND_BONDED || state == BluetoothDevice.BOND_NONE) result.complete(state)
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        else context.registerReceiver(receiver, filter)
        try {
            // Frame may already have initiated pairing on connection. Never confirm it ourselves.
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> return
                BluetoothDevice.BOND_NONE -> check(device.createBond() || device.bondState != BluetoothDevice.BOND_NONE) { "Android rejected Frame pairing" }
            }
            val bond = withTimeout(40_000) { kotlinx.coroutines.selects.select<Int> {
                result.onAwait { it }
                shutdown.onAwait { throw IOException("Frame pairing connection closed") }
            } }
            check(bond == BluetoothDevice.BOND_BONDED && device.bondState == BluetoothDevice.BOND_BONDED) { "Frame pairing was declined" }
        } finally { context.unregisterReceiver(receiver); result.cancel() }
    }
    override suspend fun write(bytes: ByteArray) {
        require(bytes.isNotEmpty() && bytes.size <= maxWriteBytes)
        check(connected.value && !closed.get())
        val g = synchronized(guard) { gatt } ?: throw IOException("Frame disconnected")
        val characteristic = tx ?: throw IOException("Frame TX missing")
        val type = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        try {
            writeAndWait(false, txId) {
                if (Build.VERSION.SDK_INT >= 33) g.writeCharacteristic(characteristic, bytes.copyOf(), type) == BluetoothStatusCodes.SUCCESS
                else {
                    @Suppress("DEPRECATION")
                    characteristic.writeType = type
                    @Suppress("DEPRECATION")
                    characteristic.value = bytes.copyOf()
                    @Suppress("DEPRECATION")
                    g.writeCharacteristic(characteristic)
                }
            }
        } catch (error: Exception) { fail(error); throw error }
    }
    private suspend fun writeAndWait(descriptor: Boolean, uuid: UUID, start: () -> Boolean) = writes.withLock {
        val operation = Write(descriptor, uuid, CompletableDeferred())
        check(!closed.get() && pending.compareAndSet(null, operation))
        try {
            check(start()) { "Android rejected Frame GATT write" }
            check(withTimeout(5_000) { operation.result.await() } == BluetoothGatt.GATT_SUCCESS) { "Frame GATT write failed" }
        } finally { pending.compareAndSet(operation, null); operation.result.cancel() }
    }
    private fun fail(error: Exception) {
        if (!closed.compareAndSet(false, true)) return
        mutableConnected.value = false
        connection.completeExceptionally(error)
        discovered.completeExceptionally(error)
        mtuResult.completeExceptionally(error)
        shutdown.complete(Unit)
        pending.getAndSet(null)?.result?.completeExceptionally(error)
        val old = synchronized(guard) { val value = gatt; gatt = null; tx = null; value }
        try { old?.disconnect() } catch (_: Exception) { }
        try { old?.close() } catch (_: Exception) { }
        messages.close(error)
    }
    override suspend fun close() { fail(IOException("Frame link closed")) }
}
