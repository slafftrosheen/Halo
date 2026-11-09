package com.halo.app.ui.screens.discovery

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.app.R
import com.halo.app.language.uiText
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import com.halo.app.ui.design.*
import com.halo.app.ui.fx.*
import com.halo.app.ui.icons.HaloGlyph
import com.halo.app.ui.presence.*
import com.halo.app.wearables.NativeWearableHost
import com.halo.app.wearables.NativeWearableControls
import com.halo.drivers.heycyan.ble.DiscoveredBleDevice
import com.halo.drivers.heycyan.ble.heyCyanCandidateLabel
import com.halo.drivers.heycyan.ble.inferGlassesFamilyFromAdvertisement
import com.halo.sdk.host.ConnectionState
import com.halo.sdk.runtime.embodiment.DeviceKind

internal enum class PairingNextAction { GRANT_ACCESS, TURN_ON, SCAN, CONNECT, WAIT, DONE, UNAVAILABLE, FOREIGN_DEVICE }
internal fun pairingShouldGuide(action: PairingNextAction, scanning: Boolean): Boolean =
    (action == PairingNextAction.SCAN && !scanning) || action == PairingNextAction.CONNECT

internal fun pairingNextAction(state: PairingUiState): PairingNextAction = when {
    state.bluetoothPrerequisite == BluetoothPrerequisite.UNSUPPORTED -> PairingNextAction.UNAVAILABLE
    state.bluetoothPrerequisite == BluetoothPrerequisite.PERMISSION_REQUIRED -> PairingNextAction.GRANT_ACCESS
    state.bluetoothPrerequisite == BluetoothPrerequisite.BLUETOOTH_OFF -> PairingNextAction.TURN_ON
    state.selectedAddress == null -> PairingNextAction.SCAN
    !pairingFamilyConnectable(state.selectedFamily) -> PairingNextAction.FOREIGN_DEVICE
    state.completedAddress == state.selectedAddress && state.connectionState == ConnectionState.Ready -> PairingNextAction.DONE
    state.isConnecting -> PairingNextAction.WAIT
    state.attemptedAddress != state.selectedAddress -> PairingNextAction.CONNECT
    state.statusMessage?.startsWith("Connection did not reach READY") == true -> PairingNextAction.CONNECT
    state.connectionState != ConnectionState.Disconnected && state.connectionState !is ConnectionState.Failed -> PairingNextAction.WAIT
    else -> PairingNextAction.CONNECT
}

internal fun pairingStage(state: PairingUiState): Int = when (pairingNextAction(state)) {
    PairingNextAction.GRANT_ACCESS, PairingNextAction.TURN_ON, PairingNextAction.UNAVAILABLE -> 1
    PairingNextAction.SCAN -> if (state.isScanning || state.devices.isNotEmpty()) 2 else 1
    else -> 3
}
internal enum class PairingSignalBand { STRONG, GOOD, WEAK }
internal fun pairingSignalBand(rssi: Int): PairingSignalBand = when {
    rssi >= -65 -> PairingSignalBand.STRONG
    rssi >= -82 -> PairingSignalBand.GOOD
    else -> PairingSignalBand.WEAK
}
internal fun pairingShortAddress(address: String): String =
    address.filter(Char::isLetterOrDigit).takeLast(4).uppercase(java.util.Locale.ROOT)

@Composable
private fun PairingSteps(stage: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(R.string.brand_pairing_ready, R.string.brand_pairing_find, R.string.brand_pairing_connect).forEachIndexed { index, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (stage >= index + 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(if (stage == index + 1) 2.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) { Text((index + 1).toString()) }
                }
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                    maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
private fun PairingDeviceRow(device: DiscoveredBleDevice, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.halo.app.ui.icons.HaloIcon(HaloGlyph.GLASSES, contentDescription = null, modifier = Modifier.size(26.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(device.advertisement.name?.takeIf(String::isNotBlank) ?: stringResource(R.string.brand_pairing_unknown_device),
                    style = MaterialTheme.typography.titleSmall)
                val signal = stringResource(when (pairingSignalBand(device.advertisement.rssi)) {
                    PairingSignalBand.STRONG -> R.string.brand_pairing_signal_strong
                    PairingSignalBand.GOOD -> R.string.brand_pairing_signal_good
                    PairingSignalBand.WEAK -> R.string.brand_pairing_signal_weak
                })
                Text(stringResource(R.string.brand_pairing_signal, signal), style = MaterialTheme.typography.labelSmall)
                val kind = inferGlassesFamilyFromAdvertisement(device.advertisement)
                val label = when {
                    NativeWearableHost.supports(kind) -> "${kind.label} · native protocol · hardware qualification pending"
                    !pairingFamilyConnectable(kind) -> "${kind.label} · vendor adapter required"
                    else -> heyCyanCandidateLabel(device.advertisement)
                }
                Text(uiText(label), style = MaterialTheme.typography.labelSmall)
                if (selected) Text(uiText("Selected"), style = MaterialTheme.typography.labelMedium)
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
        PairingNextAction.SCAN -> if (state.isScanning) "Searching nearby" else "Let's find your glasses"
        PairingNextAction.CONNECT -> "Review your selected glasses"
        PairingNextAction.WAIT -> "Connecting glasses…"
        PairingNextAction.DONE -> "Glasses ready"
        PairingNextAction.UNAVAILABLE -> "Bluetooth LE unavailable"
        PairingNextAction.FOREIGN_DEVICE -> "Different glasses protocol"
    }
    val instruction = when (next) {
        PairingNextAction.GRANT_ACCESS -> "Allow nearby-device access to find glasses. You can keep using Halo without a device."
        PairingNextAction.TURN_ON -> "Turn on Bluetooth, then search for powered-on glasses nearby."
        PairingNextAction.SCAN -> "Turn on your glasses and keep them close. You choose which device connects."
        PairingNextAction.CONNECT -> "Check the selected device below. Connect starts only when you choose it."
        PairingNextAction.WAIT -> "The connection is still being prepared. Controls become available after the device is ready."
        PairingNextAction.DONE -> "The device reports that it is ready. Recording and other controls remain explicit actions."
        PairingNextAction.UNAVAILABLE -> "This phone does not support Bluetooth Low Energy. Use Halo on the phone or another supported device."
        PairingNextAction.FOREIGN_DEVICE -> "This family requires its own vendor integration. No HeyCyan commands will be sent."
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (next) {
                PairingNextAction.WAIT -> HaloPairingFx(HaloFxPairingPhase.CONNECTING, modifier = Modifier.size(116.dp))
                PairingNextAction.DONE -> HaloPairingFx(HaloFxPairingPhase.CONNECTED, modifier = Modifier.size(116.dp))
                else -> HaloSignalFace(HaloSignalPresentation(
                    state = if (state.connectionState is ConnectionState.Failed) HaloSignalState.ERROR else HaloSignalState.IDLE,
                    glyph = if (next == PairingNextAction.SCAN) HaloGlyph.GLASSES else HaloGlyph.HALO, label = title),
                    size = 116.dp, contentDescription = uiText(title))
            }
            Text(uiText(title), style = MaterialTheme.typography.titleLarge)
            if (next == PairingNextAction.DONE) state.completedName?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        }
        Text(uiText(instruction))
        if (!state.isScanning && state.statusMessage?.startsWith("Scan failed:") == true)
            HaloFeedbackSurface(uiText("Search did not finish"), uiText("Try searching again. Check nearby-device access and keep the glasses powered on."), kind = HaloFeedbackKind.FAILURE)
        if (state.connectionState is ConnectionState.Failed)
            HaloFeedbackSurface(uiText("Connection failed"), uiText("Keep the glasses nearby and powered on. Try connecting again or select another device."), kind = HaloFeedbackKind.FAILURE)
        when (next) {
            PairingNextAction.GRANT_ACCESS -> HaloFocusAction(uiText("Allow nearby devices"), HaloGlyph.SECURITY, true, onAccess)
            PairingNextAction.TURN_ON -> HaloFocusAction(uiText("Turn on Bluetooth"), HaloGlyph.DEVICE, true, onEnable)
            PairingNextAction.SCAN -> if (!state.isScanning) {
                HaloFocusAction(uiText("Search nearby"), HaloGlyph.DEVICE, true, onScan,
                    modifier = Modifier.haloTrace(pairingShouldGuide(next, false), MaterialTheme.colorScheme.primary, lapOnAppear = true))
                Text(uiText("Nothing connects until you choose."), style = MaterialTheme.typography.bodySmall)
            }
            PairingNextAction.CONNECT -> {
                HaloFocusAction(uiText("Connect selected glasses"), HaloGlyph.GLASSES, true, onConnect,
                    modifier = Modifier.haloTrace(true, MaterialTheme.colorScheme.primary, lapOnAppear = true))
                TextButton(onClick = onScan, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.brand_pairing_search_again)) }
            }
            PairingNextAction.WAIT -> HaloProgressPresentation(uiText("Connecting glasses…"))
            PairingNextAction.DONE -> HaloStatusPresentation(HaloStatus.READY, label = uiText("Glasses ready"))
            PairingNextAction.UNAVAILABLE -> HaloStatusPresentation(HaloStatus.UNAVAILABLE)
            PairingNextAction.FOREIGN_DEVICE -> {
                HaloFeedbackSurface(uiText("Different device family"),
                    uiText("The native adapter for this family is not enabled in this build."), kind = HaloFeedbackKind.WARNING)
                TextButton(onClick = onScan, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Search again")) }
            }
        }
        if (state.isScanning) {
            HaloProgressPresentation(uiText("Searching nearby"))
            OutlinedButton(onClick = onStopScan, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Stop search")) }
        }
    }
}

@Composable
fun PairingScreen(viewModel: PairingViewModel, onRequestBluetoothPermissions: () -> Unit, onRequestBluetoothEnable: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshPrerequisites()
                Lifecycle.Event.ON_STOP -> viewModel.stopScan()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    var showOtherDevices by rememberSaveable { mutableStateOf(false) }
    val next = pairingNextAction(state)
    val shellHosted = BindShellChildPage("Add glasses", viewModel::navigateBack,
        objectId = state.selectedAddress?.takeIf { it.matches(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")) },
        sourceIdentity = "${state.selectedAddress}:${state.bluetoothPrerequisite}:$next",
        actions = listOf(
            ShellPageAction("pairing.others", "Nearby devices", HaloGlyph.DEVICE,
                state.devices.any { it.advertisement.name.isNullOrBlank() }) { showOtherDevices = !showOtherDevices },
            ShellPageAction("pairing.stop", "Stop search", HaloGlyph.STOP, state.isScanning, viewModel::stopScan),
        ))
    val ordered = state.devices.sortedByDescending { it.advertisement.rssi }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item("header") { if (!shellHosted) HaloScreenHeader(uiText("Add glasses"), navigation = viewModel::navigateBack, glyph = HaloGlyph.GLASSES) }
        if (next != PairingNextAction.SCAN) item("steps") { PairingSteps(pairingStage(state)) }
        item("guidance") { PairingGuidance(state, onRequestBluetoothPermissions, onRequestBluetoothEnable, viewModel::startScan, viewModel::stopScan, viewModel::connect) }
        if (state.selectedAddress != null && next !in setOf(PairingNextAction.WAIT, PairingNextAction.DONE)) item("family") {
            var expanded by remember { mutableStateOf(false) }
            Column {
                Text(uiText("Device family"), style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { expanded = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText(state.selectedFamily.label)) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(uiText("Automatic detection")) }, onClick = { viewModel.selectFamily(null); expanded = false })
                    DeviceKind.entries.filter { it != DeviceKind.BUDDYBOT && it != DeviceKind.UNKNOWN }.forEach { kind ->
                        DropdownMenuItem(text = { Text(uiText(kind.label)) }, onClick = { viewModel.selectFamily(kind); expanded = false })
                    }
                }
                Text(uiText("Selecting a family does not bypass its hardware handshake."), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (next == PairingNextAction.DONE) {
            item("done") {
                var editing by rememberSaveable(state.completedAddress) { mutableStateOf(false) }
                var draft by rememberSaveable(state.completedAddress) { mutableStateOf(state.completedName.orEmpty()) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (editing) {
                        HaloTextField(value = draft, onValueChange = { draft = it.take(48) }, label = { Text(uiText("Glasses name")) },
                            modifier = Modifier.fillMaxWidth(), singleLine = true, kind = VoiceFieldKind.SHORT, maxChars = 48)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { editing = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Cancel")) }
                            Button(onClick = { viewModel.renameCompletedDevice(draft); editing = false },
                                enabled = pairingValidDisplayName(draft) != null, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Save name")) }
                        }
                    } else TextButton(onClick = { draft = state.completedName.orEmpty(); editing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(uiText("Rename glasses")) }
                    if (state.statusMessage == "Could not save name") Text(uiText("Could not save name"), color = MaterialTheme.colorScheme.error)
                    Button(onClick = viewModel::navigateBack, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(uiText("Done")) }
                }
            }
            if (NativeWearableHost.supports(state.selectedFamily)) item("native_controls") {
                state.completedDeviceId?.let { NativeWearableControls(deviceId = it) }
            }
        } else {
            item("nearby_header") {
                HaloSectionHeader(uiText("Nearby devices"))
                if (ordered.isEmpty()) Text(uiText(if (state.isScanning) "Devices appear here as they are found." else "No devices found. Search again with your glasses nearby."))
            }
            items(ordered.filter { !it.advertisement.name.isNullOrBlank() }, key = { it.advertisement.address }) { device ->
                PairingDeviceRow(device, state.selectedAddress == device.advertisement.address, next != PairingNextAction.WAIT) { viewModel.selectDevice(device) }
            }
            val unnamed = ordered.filter { it.advertisement.name.isNullOrBlank() }
            if (unnamed.isNotEmpty()) item("other_device_toggle") {
                TextButton(onClick = { showOtherDevices = !showOtherDevices }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.brand_pairing_other_devices, unnamed.size) + if (showOtherDevices) " −" else " +")
                }
            }
            if (showOtherDevices) items(unnamed, key = { it.advertisement.address }) { device ->
                PairingDeviceRow(device, state.selectedAddress == device.advertisement.address, next != PairingNextAction.WAIT) { viewModel.selectDevice(device) }
            }
        }
    }
}
