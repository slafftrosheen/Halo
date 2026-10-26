package com.halo.app.ui.screens.discovery

import com.halo.app.ui.presence.BindShellChildPage
import com.halo.app.ui.presence.ShellPageAction
import com.halo.app.ui.components.HaloTextField
import com.halo.app.ui.components.VoiceFieldKind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import com.halo.app.ui.fx.haloAura
import com.halo.app.ui.fx.haloTrace
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
import com.halo.app.ui.fx.HaloPairingFx
import com.halo.app.ui.fx.HaloFxPairingPhase
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

/** A Guide may highlight only explicit discovery/connection, never permissions,
 * and never a claimed success before the selected glasses reach READY.
 */
internal fun pairingShouldGuide(action: PairingNextAction, scanning: Boolean): Boolean =
    (action == PairingNextAction.SCAN && !scanning) || action == PairingNextAction.CONNECT

/** Presentation only; discovery, admission and connection remain owned by PairingViewModel. */
internal fun pairingNextAction(state: PairingUiState): PairingNextAction = when {
    state.bluetoothPrerequisite == BluetoothPrerequisite.UNSUPPORTED -> PairingNextAction.UNAVAILABLE
    state.bluetoothPrerequisite == BluetoothPrerequisite.PERMISSION_REQUIRED -> PairingNextAction.GRANT_ACCESS
    state.bluetoothPrerequisite == BluetoothPrerequisite.BLUETOOTH_OFF -> PairingNextAction.TURN_ON
    // The wearable host's Ready state may be left over from another target.
    // Only a successful connect+save for the currently selected device is Done.
    state.selectedAddress == null -> PairingNextAction.SCAN
    state.completedAddress == state.selectedAddress &&
        state.connectionState == ConnectionState.Ready -> PairingNextAction.DONE
    // Selecting a different scan result is an explicit new attempt, even if
    // the old host session is still Connected/Ready.
    state.attemptedAddress != state.selectedAddress -> PairingNextAction.CONNECT
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            R.string.brand_pairing_ready, R.string.brand_pairing_find, R.string.brand_pairing_connect
        ).forEachIndexed { index, label ->
            val active = stage == index + 1
            val reached = stage >= index + 1
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (reached) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(if (active) 2.dp else 1.dp,
                        if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Text((index + 1).toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (reached) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(stringResource(label), style = MaterialTheme.typography.labelMedium,
                    color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
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
    technicalNames: Boolean = false,
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
                // Device identifiers can reveal hardware identity. Don't show
                // even the suffix until the user explicitly opens Technical names.
                if (technicalNames) Text(
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
        PairingNextAction.SCAN -> if (state.isScanning) "Searching nearby" else "Let's find your glasses"
        PairingNextAction.CONNECT -> "Review your selected glasses"
        PairingNextAction.WAIT -> "Connecting glasses…"
        PairingNextAction.DONE -> "Glasses ready"
        PairingNextAction.UNAVAILABLE -> "Bluetooth LE unavailable"
    }
    val instruction = when (next) {
        PairingNextAction.GRANT_ACCESS -> "Allow nearby-device access to find glasses. You can keep using Halo without a device."
        PairingNextAction.TURN_ON -> "Turn on Bluetooth, then search for powered-on glasses nearby."
        PairingNextAction.SCAN -> "Turn on your glasses and keep them close. You choose which device connects."
        PairingNextAction.CONNECT -> "Check the selected device below. Connect starts only when you choose it."
        PairingNextAction.WAIT -> "The connection is still being prepared. Controls become available after the device is ready."
        PairingNextAction.DONE -> "The device reports that it is ready. Recording and other controls remain explicit actions."
        PairingNextAction.UNAVAILABLE -> "This phone does not support Bluetooth Low Energy. Use Halo on the phone or another supported device."
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val faceState = when {
                state.connectionState is ConnectionState.Failed -> HaloSignalState.ERROR
                next == PairingNextAction.WAIT || state.isScanning -> HaloSignalState.WAITING
                else -> HaloSignalState.IDLE
            }
            when (next) {
                PairingNextAction.WAIT -> HaloPairingFx(HaloFxPairingPhase.CONNECTING,
                    modifier = Modifier.size(116.dp))
                PairingNextAction.DONE -> HaloPairingFx(HaloFxPairingPhase.CONNECTED,
                    modifier = Modifier.size(116.dp))
                else -> HaloSignalFace(
                    HaloSignalPresentation(
                        state = faceState,
                        glyph = if (next == PairingNextAction.SCAN) HaloGlyph.GLASSES else HaloGlyph.HALO,
                        label = title,
                    ),
                    modifier = if (next == PairingNextAction.SCAN && !state.isScanning)
                        Modifier.haloAura(MaterialTheme.colorScheme.primaryContainer)
                    else Modifier,
                    size = 116.dp, contentDescription = uiText(title))
            }
            Text(uiText(title), style = MaterialTheme.typography.titleLarge)
            if (next == PairingNextAction.DONE) state.completedName?.let {
                Text(it, style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(uiText(instruction))
        if (next == PairingNextAction.SCAN && state.bluetoothPrerequisite == BluetoothPrerequisite.READY) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = com.halo.app.ui.design.HaloShapes.card,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(uiText("Bluetooth is on"), style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f))
                        Icon(Icons.Default.Check, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(uiText("Glasses switched on"), style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f))
                        Text(uiText("Your turn"), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (!state.isScanning && state.statusMessage?.startsWith("Scan failed:") == true)
            HaloFeedbackSurface(uiText("Search did not finish"), uiText("Try searching again. Check nearby-device access and keep the glasses powered on."), kind = HaloFeedbackKind.FAILURE)
        if (state.connectionState is ConnectionState.Failed)
            HaloFeedbackSurface(uiText("Connection failed"), uiText("Keep the glasses nearby and powered on. Try connecting again or select another device."), kind = HaloFeedbackKind.FAILURE)
        when (next) {
            PairingNextAction.GRANT_ACCESS -> HaloFocusAction(uiText("Allow nearby devices"), HaloGlyph.SECURITY, true, onAccess)
            PairingNextAction.TURN_ON -> HaloFocusAction(uiText("Turn on Bluetooth"), HaloGlyph.DEVICE, true, onEnable)
            PairingNextAction.SCAN -> if (!state.isScanning) {
                HaloFocusAction(uiText("Search nearby"), HaloGlyph.DEVICE, true, onScan,
                    modifier = Modifier.haloTrace(
                        pairingShouldGuide(next, state.isScanning),
                        MaterialTheme.colorScheme.primary, lapOnAppear = true))
                Text(uiText("Nothing connects until you choose."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PairingNextAction.CONNECT -> {
                HaloFocusAction(uiText("Connect selected glasses"), HaloGlyph.GLASSES, true, onConnect,
                    modifier = Modifier.haloTrace(
                        pairingShouldGuide(next, state.isScanning),
                        MaterialTheme.colorScheme.primary, lapOnAppear = true))
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
    var showTechnicalNames by rememberSaveable { mutableStateOf(false) }
    val shellHosted = BindShellChildPage("Add glasses", viewModel::navigateBack,
        objectId = state.selectedAddress?.takeIf { it.matches(Regex("[0-9A-Fa-f]{2}(:[0-9A-Fa-f]{2}){5}")) }, sourceIdentity = "${state.selectedAddress}:${state.bluetoothPrerequisite}:${pairingNextAction(state)}",
        actions = listOf(
            ShellPageAction("pairing.others", "Nearby devices", HaloGlyph.DEVICE,
                state.devices.any { it.advertisement.name.isNullOrBlank() }) { showOtherDevices = !showOtherDevices },
            ShellPageAction("pairing.stop", "Stop search", HaloGlyph.STOP, state.isScanning, viewModel::stopScan),
        ))
    val ordered = state.devices.sortedByDescending { it.advertisement.rssi }
    val named = ordered.filter { !it.advertisement.name.isNullOrBlank() }
    val unnamed = ordered.filter { it.advertisement.name.isNullOrBlank() }
    val canSelect = state.bluetoothPrerequisite == BluetoothPrerequisite.READY &&
        pairingNextAction(state) !in setOf(PairingNextAction.WAIT, PairingNextAction.DONE)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(key = "header") {
            if (!shellHosted) HaloScreenHeader(uiText("Add glasses"),
                navigation = viewModel::navigateBack, glyph = HaloGlyph.GLASSES)
        }
        if (pairingNextAction(state) != PairingNextAction.SCAN) {
            item(key = "steps") { PairingSteps(pairingStage(state)) }
        }
        item(key = "guidance") {
            PairingGuidance(state, onRequestBluetoothPermissions, onRequestBluetoothEnable,
                viewModel::startScan, viewModel::stopScan, viewModel::connect)
        }
        if (pairingNextAction(state) == PairingNextAction.DONE) item(key = "done") {
            var editingName by rememberSaveable(state.completedAddress) { mutableStateOf(false) }
            var nameDraft by rememberSaveable(state.completedAddress) {
                mutableStateOf(state.completedName.orEmpty())
            }
            LaunchedEffect(state.completedName, editingName) {
                if (!editingName) nameDraft = state.completedName.orEmpty()
            }
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editingName) {
                    HaloTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it.take(48) },
                        label = { Text(uiText("Glasses name")) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, kind = VoiceFieldKind.SHORT, maxChars = 48)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { editingName = false },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(uiText("Cancel")) }
                        Button(
                            onClick = { viewModel.renameCompletedDevice(nameDraft); editingName = false },
                            enabled = pairingValidDisplayName(nameDraft) != null &&
                                pairingValidDisplayName(nameDraft) != state.completedName,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                        ) { Text(uiText("Save name")) }
                    }
                } else {
                    TextButton(
                        onClick = { editingName = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text(uiText("Rename glasses")) }
                }
                if (state.statusMessage == "Could not save name") {
                    Text(uiText("Could not save name"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                Button(onClick = viewModel::navigateBack,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(uiText("Done"))
                }
            }
        }
        if (pairingNextAction(state) != PairingNextAction.DONE) {
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
                onClick = { viewModel.selectDevice(device) },
                technicalNames = false)
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
                onClick = { viewModel.selectDevice(device) },
                technicalNames = false)
            }
        }
    }
}
