package com.halo.app.ui.screens.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import com.halo.app.R
import com.halo.app.ui.presence.HaloSignalFace
import com.halo.app.ui.presence.HaloSignalPresentation
import com.halo.app.ui.presence.HaloSignalState
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
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
    // A previous host attempt can leave a stale Connecting state. A new scan
    // has no selected target and must never be presented as that old attempt.
    state.selectedAddress == null -> PairingNextAction.SCAN
    // A timeout is not infinite "connecting", even if the host's last state
    // still says Connecting. Offer a genuine retry without pretending readiness.
    state.statusMessage?.startsWith("Connection did not reach READY") == true &&
        state.selectedAddress != null -> PairingNextAction.CONNECT
    state.connectionState != ConnectionState.Disconnected && state.connectionState !is ConnectionState.Failed -> PairingNextAction.WAIT
    state.selectedAddress != null -> PairingNextAction.CONNECT
    else -> PairingNextAction.SCAN
}


/** Three observable stages; the progress display never starts a Bluetooth operation. */
internal fun pairingStage(state: PairingUiState): Int = when (pairingNextAction(state)) {
    PairingNextAction.GRANT_ACCESS, PairingNextAction.TURN_ON, PairingNextAction.UNAVAILABLE -> 1
    PairingNextAction.SCAN -> if (state.isScanning || state.devices.isNotEmpty()) 2 else 1
    PairingNextAction.CONNECT, PairingNextAction.WAIT, PairingNextAction.DONE -> 3
}

/** An RSSI band, not a claim of physical distance (device power varies). */
internal enum class PairingSignalBand { STRONG, GOOD, WEAK }
internal fun pairingSignalBand(rssi: Int): PairingSignalBand = when {
    rssi >= -65 -> PairingSignalBand.STRONG
    rssi >= -82 -> PairingSignalBand.GOOD
    else -> PairingSignalBand.WEAK
}

/** Expose only the trailing four address characters in user-facing discovery. */
internal fun pairingShortAddress(address: String): String =
    address.filter(Char::isLetterOrDigit).takeLast(4).uppercase(java.util.Locale.ROOT)

@Composable
private fun PairingSteps(stage: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            progress = { stage / 3f }, modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.brand_pairing_ready, R.string.brand_pairing_find, R.string.brand_pairing_connect
            ).forEachIndexed { index, label ->
                Text(
                    "${index + 1} · " + stringResource(label),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (stage == index + 1) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun PairingDeviceRow(
    device: DiscoveredBleDevice,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            com.halo.app.ui.icons.HaloIcon(
                HaloGlyph.GLASSES, contentDescription = null, modifier = Modifier.size(26.dp),
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurface
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    device.advertisement.name?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.brand_pairing_unknown_device),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(
                        R.string.brand_pairing_device_suffix,
                        pairingShortAddress(device.advertisement.address)
                    ),
                    style = MaterialTheme.typography.bodySmall
                )
                val signalText = stringResource(when (pairingSignalBand(device.advertisement.rssi)) {
                    PairingSignalBand.STRONG -> R.string.brand_pairing_signal_strong
                    PairingSignalBand.GOOD -> R.string.brand_pairing_signal_good
                    PairingSignalBand.WEAK -> R.string.brand_pairing_signal_weak
                })
                Text(stringResource(R.string.brand_pairing_signal, signalText),
                    style = MaterialTheme.typography.labelSmall)
                if (selected) Text(uiText("Selected"),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
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
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val faceState = when {
                state.connectionState is ConnectionState.Failed -> HaloSignalState.ERROR
                next == PairingNextAction.DONE -> HaloSignalState.SUCCESS
                next == PairingNextAction.WAIT || state.isScanning -> HaloSignalState.WAITING
                else -> HaloSignalState.IDLE
            }
            HaloSignalFace(
                HaloSignalPresentation(state = faceState, label = title),
                size = 88.dp, contentDescription = uiText(title)
            )
            Text(uiText(title), style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f))
        }
        Text(uiText(instruction))
        if (!state.isScanning && state.statusMessage?.startsWith("Scan failed:") == true)
            HaloFeedbackSurface(uiText("Search did not finish"), uiText("Try searching again. Check nearby-device access and keep the glasses powered on."), kind = HaloFeedbackKind.FAILURE)
        if (state.connectionState is ConnectionState.Failed)
            HaloFeedbackSurface(uiText("Connection failed"), uiText("Keep the glasses nearby and powered on. Try connecting again or select another device."), kind = HaloFeedbackKind.FAILURE)
        when (next) {
            PairingNextAction.GRANT_ACCESS -> HaloFocusAction(uiText("Allow nearby devices"), HaloGlyph.SECURITY, true, onAccess)
            PairingNextAction.TURN_ON -> HaloFocusAction(uiText("Turn on Bluetooth"), HaloGlyph.DEVICE, true, onEnable)
            PairingNextAction.SCAN -> if (!state.isScanning) HaloFocusAction(uiText("Search nearby"), HaloGlyph.DEVICE, true, onScan)
            PairingNextAction.CONNECT -> {
                HaloFocusAction(uiText("Connect selected glasses"), HaloGlyph.GLASSES, true, onConnect)
                TextButton(onClick = onScan, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.brand_pairing_search_again))
                }
            }
            PairingNextAction.WAIT -> HaloProgressPresentation(uiText("Connecting glasses…"))
            PairingNextAction.DONE -> HaloStatusPresentation(HaloStatus.READY, label = uiText("Glasses ready"))
            PairingNextAction.UNAVAILABLE -> HaloStatusPresentation(HaloStatus.UNAVAILABLE)
        }
        if (state.isScanning) {
            HaloProgressPresentation(uiText("Searching nearby"))
            com.halo.app.ui.components.HaloActionButton(onStopScan, style = HaloActionStyle.SECONDARY) { Text(uiText("Stop search")) }
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
    var showOtherDevices by rememberSaveable { mutableStateOf(false) }
    val ordered = state.devices.sortedByDescending { it.advertisement.rssi }
    val named = ordered.filter { !it.advertisement.name.isNullOrBlank() }
    val unnamed = ordered.filter { it.advertisement.name.isNullOrBlank() }
    val canSelect = state.bluetoothPrerequisite == BluetoothPrerequisite.READY &&
        pairingNextAction(state) !in setOf(PairingNextAction.WAIT, PairingNextAction.DONE)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") {
            HaloScreenHeader(uiText("Pair glasses"), uiText("Choose a supported device"),
                navigation = viewModel::navigateBack, glyph = HaloGlyph.GLASSES)
        }
        item(key = "steps") { PairingSteps(pairingStage(state)) }
        item(key = "guidance") {
            PairingGuidance(state, onRequestBluetoothPermissions, onRequestBluetoothEnable,
                viewModel::startScan, viewModel::stopScan, viewModel::connect)
        }
        if (pairingNextAction(state) == PairingNextAction.DONE) item(key = "done") {
            Button(onClick = viewModel::navigateBack,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(uiText("Done")) }
        }
        item(key = "nearby_header") {
            HaloSectionHeader(uiText("Nearby devices"))
            if (state.devices.isEmpty()) Text(uiText(if (state.isScanning)
                "Devices appear here as they are found."
                else "No devices found. Search again with your glasses nearby."))
        }
        items(named, key = { it.advertisement.address }) { device ->
            PairingDeviceRow(device,
                selected = state.selectedAddress == device.advertisement.address,
                enabled = canSelect,
                onClick = { viewModel.selectDevice(device) })
        }
        if (unnamed.isNotEmpty()) item(key = "other_device_toggle") {
            TextButton(onClick = { showOtherDevices = !showOtherDevices },
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.brand_pairing_other_devices, unnamed.size) +
                    if (showOtherDevices) " −" else " +")
            }
        }
        if (showOtherDevices) items(unnamed, key = { it.advertisement.address }) { device ->
            PairingDeviceRow(device,
                selected = state.selectedAddress == device.advertisement.address,
                enabled = canSelect,
                onClick = { viewModel.selectDevice(device) })
        }
    }
}
