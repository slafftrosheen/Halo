package com.halo.app.ui.screens.devices

import android.view.InputDevice
import android.view.MotionEvent
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.ui.components.HaloActionButton
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.design.HaloSpacing
import com.halo.sdk.runtime.hardware.InputRole
import com.halo.sdk.runtime.hardware.InventoryAvailability
import com.halo.sdk.runtime.hardware.PeripheralInventory
import com.halo.sdk.runtime.hardware.PeripheralKind
import com.halo.sdk.runtime.hardware.PeripheralProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** OS reports are evidence for inspection, never automatic model or protocol qualification. */
@Composable
internal fun PeripheralInventoryPanel(owner: PeripheralInventory) {
    val state by owner.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    // A changed inventory can represent a different physical connection under the same OS id.
    var expandedToken by remember(state.sourceVersion) { mutableStateOf<String?>(null) }
    var showAll by remember(state.sourceVersion) { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var refreshFailed by remember(state.sourceVersion) { mutableStateOf(false) }
    val devices = state.devices.filter { item ->
        val profile = item.profile
        profile.kind == PeripheralKind.USB || profile.virtual != true &&
            (profile.external == true || profile.roles.any { it in visibleInputRoles })
    }
    val visible = if (showAll) devices else devices.take(COMPACT_DEVICE_COUNT)
    val available = state.inputAvailability != InventoryAvailability.BACKGROUND || state.usbAvailability != InventoryAvailability.BACKGROUND

    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(HaloSpacing.lg), verticalArrangement = Arrangement.spacedBy(HaloSpacing.md)) {
            Text(stringResource(R.string.peripheral_inventory_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.peripheral_inventory_help), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.peripheral_inventory_input_status,
                stringResource(availabilityLabel(state.inputAvailability))), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.peripheral_inventory_usb_status,
                stringResource(availabilityLabel(state.usbAvailability))), style = MaterialTheme.typography.bodySmall)
            if (available && devices.isEmpty()) {
                Text(stringResource(R.string.peripheral_inventory_empty), style = MaterialTheme.typography.bodySmall)
            }
            visible.forEach { item ->
                val profile = item.profile
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(HaloSpacing.sm)) {
                    Text(profile.name, style = MaterialTheme.typography.titleSmall)
                    val kind = stringResource(if (profile.kind == PeripheralKind.USB) R.string.peripheral_kind_usb else R.string.peripheral_kind_input)
                    val roles = profile.roles.map { stringResource(roleLabel(it)) }.joinToString(" · ")
                    Text(if (roles.isEmpty()) kind else "$kind · $roles", style = MaterialTheme.typography.bodySmall)
                    if (profile.hasStylusAxis(MotionEvent.AXIS_PRESSURE)) {
                        Text(stringResource(R.string.peripheral_inventory_pressure), style = MaterialTheme.typography.bodySmall)
                    }
                    if (profile.hasStylusAxis(MotionEvent.AXIS_TILT)) {
                        Text(stringResource(R.string.peripheral_inventory_tilt), style = MaterialTheme.typography.bodySmall)
                    }
                    HaloActionButton(onClick = { expandedToken = item.token.takeUnless { expandedToken == it } },
                        style = HaloActionStyle.UTILITY) {
                        Text(stringResource(if (expandedToken == item.token) R.string.peripheral_inventory_hide_details else R.string.peripheral_inventory_details))
                    }
                    if (expandedToken == item.token) {
                        Text(stringResource(R.string.peripheral_inventory_reported), style = MaterialTheme.typography.bodySmall)
                        val unknown = stringResource(R.string.peripheral_inventory_unknown)
                        Text(stringResource(R.string.peripheral_inventory_ids, profile.vendorId?.hexId() ?: unknown,
                            profile.productId?.hexId() ?: unknown), style = MaterialTheme.typography.bodySmall)
                        if (profile.kind == PeripheralKind.INPUT) {
                            Text(stringResource(R.string.peripheral_inventory_location,
                                stringResource(when (profile.external) {
                                    true -> R.string.peripheral_inventory_external
                                    false -> R.string.peripheral_inventory_builtin
                                    null -> R.string.peripheral_inventory_unknown
                                })), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.peripheral_inventory_axes, profile.axes.size, profile.axisCount),
                                style = MaterialTheme.typography.bodySmall)
                            profile.axes.forEach { axis ->
                                Text(stringResource(R.string.peripheral_inventory_axis, MotionEvent.axisToString(axis.axis),
                                    axis.source.hexId(), axis.min.toString(), axis.max.toString(), axis.resolution.toString()),
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            Text(stringResource(R.string.peripheral_inventory_usb_class, profile.usbClass?.toString() ?: unknown,
                                profile.usbSubclass?.toString() ?: unknown, profile.usbProtocol?.toString() ?: unknown),
                                style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.peripheral_inventory_permission,
                                stringResource(when (profile.usbPermission) {
                                    true -> R.string.peripheral_inventory_granted
                                    false -> R.string.peripheral_inventory_not_granted
                                    null -> R.string.peripheral_inventory_unknown
                                })), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.peripheral_inventory_interfaces, profile.interfaces.size, profile.interfaceCount),
                                style = MaterialTheme.typography.bodySmall)
                            profile.interfaces.forEach { usbInterface ->
                                Text(stringResource(R.string.peripheral_inventory_interface, usbInterface.id, usbInterface.alternateSetting,
                                    usbInterface.deviceClass, usbInterface.subclass, usbInterface.protocol, usbInterface.endpointCount),
                                    style = MaterialTheme.typography.bodySmall)
                                usbInterface.endpoints.forEach { endpoint ->
                                    Text(stringResource(R.string.peripheral_inventory_endpoint, endpoint.address.hexId(), endpoint.direction,
                                        endpoint.type, endpoint.maxPacketSize, endpoint.interval), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (profile.detailsTruncated) Text(stringResource(R.string.peripheral_inventory_details_limited),
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (devices.size > COMPACT_DEVICE_COUNT) {
                HaloActionButton(onClick = { showAll = !showAll }, style = HaloActionStyle.UTILITY) {
                    Text(stringResource(if (showAll) R.string.peripheral_inventory_show_fewer else R.string.peripheral_inventory_show_all,
                        devices.size))
                }
            }
            if (state.truncated) Text(stringResource(R.string.peripheral_inventory_limited), style = MaterialTheme.typography.bodySmall)
            HaloActionButton(onClick = {
                refreshing = true
                refreshFailed = false
                scope.launch {
                    try { owner.snapshot() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { refreshFailed = true }
                    finally { refreshing = false }
                }
            }, enabled = available && !refreshing, style = HaloActionStyle.UTILITY) {
                Text(stringResource(if (refreshing) R.string.peripheral_inventory_refreshing else R.string.peripheral_inventory_refresh))
            }
            if (refreshFailed) Text(stringResource(R.string.peripheral_inventory_refresh_failed),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

private const val COMPACT_DEVICE_COUNT = 4
private val visibleInputRoles = setOf(InputRole.STYLUS, InputRole.MOUSE, InputRole.GAMEPAD, InputRole.JOYSTICK)
private fun PeripheralProfile.hasStylusAxis(axisId: Int): Boolean = InputRole.STYLUS in roles && axes.any {
    it.valid && it.axis == axisId && it.source and InputDevice.SOURCE_STYLUS == InputDevice.SOURCE_STYLUS
}
private fun Int.hexId() = "0x${toUInt().toString(16).uppercase().padStart(4, '0')}"
@StringRes private fun availabilityLabel(value: InventoryAvailability) = when (value) {
    InventoryAvailability.AVAILABLE -> R.string.peripheral_inventory_available
    InventoryAvailability.PARTIAL -> R.string.peripheral_inventory_partial
    InventoryAvailability.UNAVAILABLE -> R.string.peripheral_inventory_unavailable
    InventoryAvailability.BACKGROUND -> R.string.peripheral_inventory_background
}
@StringRes private fun roleLabel(value: InputRole) = when (value) {
    InputRole.STYLUS -> R.string.peripheral_role_stylus
    InputRole.MOUSE -> R.string.peripheral_role_mouse
    InputRole.TOUCH -> R.string.peripheral_role_touch
    InputRole.KEYBOARD -> R.string.peripheral_role_keyboard
    InputRole.GAMEPAD -> R.string.peripheral_role_gamepad
    InputRole.JOYSTICK -> R.string.peripheral_role_joystick
    InputRole.ROTARY -> R.string.peripheral_role_rotary
}
