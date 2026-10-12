package com.halo.app.ui.screens.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloActionStyle
import com.halo.app.ui.design.*
import com.halo.app.ui.icons.HaloGlyph
import com.halo.sdk.host.ConnectionState

internal enum class PairingNextAction { GRANT_ACCESS, TURN_ON, SCAN, CONNECT, WAIT, DONE, UNAVAILABLE }

/** Presentation only; discovery, admission and connection remain owned by PairingViewModel. */
internal fun pairingNextAction(state: PairingUiState): PairingNextAction = when {
    state.bluetoothPrerequisite == BluetoothPrerequisite.UNSUPPORTED -> PairingNextAction.UNAVAILABLE
    state.bluetoothPrerequisite == BluetoothPrerequisite.PERMISSION_REQUIRED -> PairingNextAction.GRANT_ACCESS
    state.bluetoothPrerequisite == BluetoothPrerequisite.BLUETOOTH_OFF -> PairingNextAction.TURN_ON
    state.connectionState == ConnectionState.Ready -> PairingNextAction.DONE
    state.connectionState != ConnectionState.Disconnected && state.connectionState !is ConnectionState.Failed -> PairingNextAction.WAIT
    state.selectedAddress != null -> PairingNextAction.CONNECT
    else -> PairingNextAction.SCAN
}

@Composable
internal fun PairingGuidance(state: PairingUiState, onAccess: () -> Unit, onEnable: () -> Unit,
    onScan: () -> Unit, onStopScan: () -> Unit, onConnect: () -> Unit) {
    val next = pairingNextAction(state)
    val title = when (next) {
        PairingNextAction.GRANT_ACCESS -> "Bluetooth access needed"
        PairingNextAction.TURN_ON -> "Bluetooth is off"
        PairingNextAction.SCAN -> if (state.isScanning) "Searching nearby" else "Ready to scan"
        PairingNextAction.CONNECT -> "Review your selected glasses"
        PairingNextAction.WAIT -> "Connecting glasses…"
        PairingNextAction.DONE -> "Glasses ready"
        PairingNextAction.UNAVAILABLE -> "Bluetooth LE unavailable"
    }
    val instruction = when (next) {
        PairingNextAction.GRANT_ACCESS -> "Allow nearby-device access to find glasses. You can keep using Halo without a device."
        PairingNextAction.TURN_ON -> "Turn on Bluetooth, then search for powered-on glasses nearby."
        PairingNextAction.SCAN -> "Power on your glasses, search nearby, then select the device you want. Searching does not connect it."
        PairingNextAction.CONNECT -> "Check the selected device below. Connect starts only when you choose it."
        PairingNextAction.WAIT -> "The connection is still being prepared. Controls become available after the device is ready."
        PairingNextAction.DONE -> "The device reports that it is ready. Recording and other controls remain explicit actions."
        PairingNextAction.UNAVAILABLE -> "This phone does not support Bluetooth Low Energy. Use Halo on the phone or another supported device."
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(uiText(title), style = MaterialTheme.typography.titleMedium)
        Text(uiText(instruction))
        if (!state.isScanning && state.statusMessage?.startsWith("Scan failed:") == true)
            HaloFeedbackSurface(uiText("Search did not finish"), uiText("Try searching again. Check nearby-device access and keep the glasses powered on."), kind = HaloFeedbackKind.FAILURE)
        if (state.connectionState is ConnectionState.Failed)
            HaloFeedbackSurface(uiText("Connection failed"), uiText("Keep the glasses nearby and powered on. Try connecting again or select another device."), kind = HaloFeedbackKind.FAILURE)
        when (next) {
            PairingNextAction.GRANT_ACCESS -> HaloFocusAction(uiText("Allow nearby devices"), HaloGlyph.SECURITY, true, onAccess)
            PairingNextAction.TURN_ON -> HaloFocusAction(uiText("Turn on Bluetooth"), HaloGlyph.DEVICE, true, onEnable)
            PairingNextAction.SCAN -> if (!state.isScanning) HaloFocusAction(uiText("Search nearby"), HaloGlyph.DEVICE, true, onScan)
            PairingNextAction.CONNECT -> HaloFocusAction(uiText("Connect selected glasses"), HaloGlyph.GLASSES, true, onConnect)
            PairingNextAction.WAIT -> HaloProgressPresentation(uiText("Connecting glasses…"))
            PairingNextAction.DONE -> HaloStatusPresentation(HaloStatus.READY, label = uiText("Glasses ready"))
            PairingNextAction.UNAVAILABLE -> HaloStatusPresentation(HaloStatus.UNAVAILABLE)
        }
        if (state.isScanning) {
            HaloProgressPresentation(uiText("Searching nearby"))
            com.halo.app.ui.components.HaloActionButton(onStopScan, style = HaloActionStyle.STOP) { Text(uiText("Stop search")) }
        }
    }
}

@Composable
fun PairingScreen(viewModel: PairingViewModel, onRequestBluetoothPermissions: () -> Unit,
    onRequestBluetoothEnable: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshPrerequisites()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { HaloScreenHeader(uiText("Pair glasses"), uiText("Choose a supported device"), navigation = viewModel::navigateBack, glyph = HaloGlyph.GLASSES) }
        item {
            PairingGuidance(state, onRequestBluetoothPermissions, onRequestBluetoothEnable,
                viewModel::startScan, viewModel::stopScan, viewModel::connect)
        }
        item {
            HaloSectionHeader(uiText("Nearby devices"))
            if (state.devices.isEmpty()) Text(uiText(if (state.isScanning) "Devices appear here as they are found." else "No devices found. Search again with your glasses nearby."))
        }
        items(state.devices, key = { it.advertisement.address }) { device ->
            val selected = state.selectedAddress == device.advertisement.address
            Surface(onClick = { viewModel.selectDevice(device) },
                enabled = state.bluetoothPrerequisite == BluetoothPrerequisite.READY && pairingNextAction(state) != PairingNextAction.WAIT,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium,
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(device.advertisement.name ?: uiText("Unknown glasses"), style = MaterialTheme.typography.titleMedium)
                    Text(device.advertisement.address, style = MaterialTheme.typography.bodySmall)
                    if (selected) Text(uiText("Selected"), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
