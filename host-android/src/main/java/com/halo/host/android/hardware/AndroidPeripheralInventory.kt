package com.halo.host.android.hardware

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import com.halo.sdk.runtime.hardware.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

/** Read-only OS metadata. Never opens USB, claims HID, requests permission, or reads serial numbers. */
class AndroidPeripheralInventory(context: Context, private val canInspect: () -> Boolean) : PeripheralInventory {
    private val context = context.applicationContext
    private val input = context.getSystemService(InputManager::class.java)
    private val usb = context.getSystemService(UsbManager::class.java)
    private val session = PeripheralInventorySession { UUID.randomUUID().toString() }
    private val mutableState = MutableStateFlow(session.state)
    override val state = mutableState.asStateFlow()
    private val hosts = mutableSetOf<Any>()
    private var listening = false
    private var receiving = false
    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = refresh(setOf("input:$deviceId"))
        override fun onInputDeviceChanged(deviceId: Int) = refresh(setOf("input:$deviceId"))
        override fun onInputDeviceRemoved(deviceId: Int) = refresh(setOf("input:$deviceId"))
    }
    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action !in setOf(UsbManager.ACTION_USB_DEVICE_ATTACHED, UsbManager.ACTION_USB_DEVICE_DETACHED)) return
            // The broadcast is a refresh hint, never connection or permission authority.
            // Invalidate USB selections even when a detach/reconnect reused the same OS path.
            refresh(invalidateUsb = true)
        }
    }

    fun resume(host: Any) {
        checkMain()
        hosts.add(host)
        if (!listening && input != null) {
            input.registerInputDeviceListener(inputListener, Handler(Looper.getMainLooper()))
            listening = true
        }
        if (!receiving && usb != null) {
            val filter = IntentFilter().apply { addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED); addAction(UsbManager.ACTION_USB_DEVICE_DETACHED) }
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") context.registerReceiver(usbReceiver, filter)
            receiving = true
        }
        refresh()
    }

    fun pause(host: Any) {
        checkMain()
        hosts.remove(host)
        if (hosts.isNotEmpty()) return
        if (listening) input?.unregisterInputDeviceListener(inputListener)
        if (receiving) context.unregisterReceiver(usbReceiver)
        listening = false
        receiving = false
        session.stop()
        publish()
    }

    override suspend fun snapshot(): PeripheralInventorySnapshot = withContext(Dispatchers.Main.immediate) { refresh(); state.value }

    private fun refresh(invalidated: Set<String> = emptySet(), invalidateUsb: Boolean = false) {
        checkMain()
        if (hosts.isEmpty() || !canInspect()) { session.stop(); publish(); return }
        val observations = mutableListOf<PeripheralObservation>()
        var inputCount = 0
        var usbCount = 0
        var inputStatus = InventoryAvailability.UNAVAILABLE
        var usbStatus = InventoryAvailability.UNAVAILABLE
        if (input != null) try {
            val ids = input.inputDeviceIds.sorted()
            inputCount = ids.size
            inputStatus = InventoryAvailability.AVAILABLE
            ids.take(32).forEach { id ->
                val device = input.getInputDevice(id)
                if (device == null) inputStatus = InventoryAvailability.PARTIAL
                else observations.add(PeripheralObservation("input:$id", inputProfile(device)))
            }
        } catch (_: SecurityException) { inputStatus = InventoryAvailability.UNAVAILABLE }
        if (usb != null) try {
            val devices = usb.deviceList.values.sortedBy { it.deviceName }
            usbCount = devices.size
            usbStatus = InventoryAvailability.AVAILABLE
            devices.take(16).forEach { device ->
                observations.add(PeripheralObservation("usb:${device.deviceName}", usbProfile(device, usb.hasPermission(device))))
            }
        } catch (_: SecurityException) { usbStatus = InventoryAvailability.UNAVAILABLE }
        session.update(observations, inputStatus, usbStatus, inputCount, usbCount,
            invalidated + if (invalidateUsb) observations.filter { it.profile.kind == PeripheralKind.USB }.map { it.platformKey } else emptyList())
        publish()
    }

    private fun inputProfile(device: InputDevice): PeripheralProfile {
        val roles = linkedMapOf(InputDevice.SOURCE_STYLUS to InputRole.STYLUS, InputDevice.SOURCE_MOUSE to InputRole.MOUSE,
            InputDevice.SOURCE_TOUCHSCREEN to InputRole.TOUCH, InputDevice.SOURCE_KEYBOARD to InputRole.KEYBOARD,
            InputDevice.SOURCE_GAMEPAD to InputRole.GAMEPAD, InputDevice.SOURCE_JOYSTICK to InputRole.JOYSTICK,
            InputDevice.SOURCE_ROTARY_ENCODER to InputRole.ROTARY).filterKeys(device::supportsSource).values.toSet()
        val ranges = device.motionRanges
        return PeripheralProfile(PeripheralKind.INPUT, device.name, device.vendorId.takeIf { it != 0 }, device.productId.takeIf { it != 0 },
            roles = roles, axes = ranges.take(PeripheralInventorySession.MAX_AXES).map {
                InputAxisReport(it.axis, it.source, it.min, it.max, it.flat, it.fuzz, it.resolution)
            }, axisCount = ranges.size, external = if (Build.VERSION.SDK_INT >= 29) device.isExternal else null, virtual = device.isVirtual)
    }

    private fun usbProfile(device: UsbDevice, permission: Boolean): PeripheralProfile = PeripheralProfile(
        PeripheralKind.USB, device.productName ?: device.manufacturerName ?: "USB peripheral", device.vendorId, device.productId,
        usbClass = device.deviceClass, usbSubclass = device.deviceSubclass, usbProtocol = device.deviceProtocol,
        interfaceCount = device.interfaceCount, usbPermission = permission,
        interfaces = (0 until device.interfaceCount.coerceAtMost(PeripheralInventorySession.MAX_INTERFACES)).map { index ->
            val item = device.getInterface(index)
            UsbInterfaceReport(item.id, item.alternateSetting, item.interfaceClass, item.interfaceSubclass, item.interfaceProtocol,
                (0 until item.endpointCount.coerceAtMost(PeripheralInventorySession.MAX_ENDPOINTS)).map { endpointIndex ->
                    val endpoint = item.getEndpoint(endpointIndex)
                    UsbEndpointReport(endpoint.address, endpoint.direction, endpoint.type, endpoint.maxPacketSize, endpoint.interval)
                }, item.endpointCount)
        })

    private fun publish() { mutableState.value = session.state }
    private fun checkMain() { check(Looper.myLooper() == Looper.getMainLooper()) }
}
