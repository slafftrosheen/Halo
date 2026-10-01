package com.halo.app.audio

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.halo.host.android.audio.BluetoothAudioEndpoint
import com.halo.host.android.audio.BluetoothAudioRouteMonitor
import com.halo.app.audio.association.WearableAudioAssociation
import com.halo.app.audio.association.WearableAudioAssociationRepository
import com.halo.sdk.runtime.HaloDispatchers
import com.halo.sdk.runtime.Storage
import com.halo.sdk.runtime.productionHaloDispatchers
import java.util.regex.Pattern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface HeyCyanAudioPairingState {
    data object Unknown : HeyCyanAudioPairingState
    data object NotAssociated : HeyCyanAudioPairingState
    data object Discovering : HeyCyanAudioPairingState
    data object AwaitingUserConfirmation : HeyCyanAudioPairingState
    data class Bonding(val association: WearableAudioAssociation) : HeyCyanAudioPairingState
    data class Associated(val association: WearableAudioAssociation) : HeyCyanAudioPairingState
    data class AudioConnected(
        val association: WearableAudioAssociation,
        val endpoint: BluetoothAudioEndpoint
    ) : HeyCyanAudioPairingState
    data class Failed(val message: String, val repairable: Boolean = true) : HeyCyanAudioPairingState
}

/** Public-API association, bonding, persistence, and continuous audio-route observation. */
class HeyCyanAudioPairingCoordinator(
    context: Context,
    storage: Storage,
    private val routeMonitor: BluetoothAudioRouteMonitor,
    private val dispatchers: HaloDispatchers = productionHaloDispatchers()
) {
    private val appContext = context.applicationContext
    private val companionManager = appContext.getSystemService(CompanionDeviceManager::class.java)
    private val bluetoothAdapter = appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val repository = WearableAudioAssociationRepository(storage, dispatchers)
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val _state = MutableStateFlow<HeyCyanAudioPairingState>(HeyCyanAudioPairingState.Unknown)
    val state: StateFlow<HeyCyanAudioPairingState> = _state.asStateFlow()
    private val _confirmationRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val confirmationRequests: SharedFlow<IntentSender> = _confirmationRequests.asSharedFlow()
    @Volatile private var activeWearableId: String? = null
    @Volatile private var association: WearableAudioAssociation? = null
    private val restoreMutex = Mutex()
    private val adoptMutex = Mutex()

    private val bondReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val current = association ?: return
            if (!hasConnectPermission()) return
            if (!sameAddress(current.bluetoothAddress, device.address)) return
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> scope.launch { updateBonded(current, true, "bond_event") }
                BluetoothDevice.BOND_NONE -> {
                    logState("FAILED", reason = "Audio bond was removed", source = "bond_event")
                    _state.value = HeyCyanAudioPairingState.Failed("Audio bond was removed")
                }
            }
        }
    }

    init {
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            appContext.registerReceiver(bondReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            appContext.registerReceiver(bondReceiver, filter)
        }
        scope.launch {
            routeMonitor.routes.collectLatest { routes -> reconcileRoute(routes, "route_monitor") }
        }
    }

    fun restore(wearableDeviceId: String) {
        scope.launch { ensureRestored(wearableDeviceId) }
    }

    /**
     * Process-level, UI-independent association restoration.
     *
     * Speech routing calls this before evaluating pairing state so a fresh process
     * can answer on already-paired glasses even when the Devices screen was never opened.
     */
    suspend fun ensureRestored(wearableDeviceId: String) = restoreMutex.withLock {
        val currentState = _state.value
        if (activeWearableId == wearableDeviceId &&
            currentState !is HeyCyanAudioPairingState.Unknown
        ) {
            return@withLock
        }

        activeWearableId = wearableDeviceId
        val saved = repository.read(wearableDeviceId)

        // Check system CompanionDeviceManager associations first
        val matchingSystemAssoc = findMatchingSystemAssociation(saved?.bluetoothAddress)
        val assocToUse = if (matchingSystemAssoc != null && saved != null) {
            saved.copy(associationId = matchingSystemAssoc.first, bluetoothAddress = matchingSystemAssoc.second ?: saved.bluetoothAddress)
        } else saved

        association = assocToUse
        if (assocToUse == null) {
            logState("NOT_ASSOCIATED", source = "restore")
            _state.value = HeyCyanAudioPairingState.NotAssociated
        } else if (!associationStillExists(assocToUse)) {
            logState("FAILED", reason = "Audio association needs repair", source = "restore")
            _state.value = HeyCyanAudioPairingState.Failed("Audio association needs repair")
        } else {
            val device = assocToUse.bluetoothAddress?.let(::remoteDeviceOrNull)
            val isBonded = isDeviceBonded(device)
            val updated = assocToUse.copy(bonded = isBonded)
            association = updated
            repository.write(updated)
            if (isBonded) {
                logState("ASSOCIATED", source = "restore")
                _state.value = HeyCyanAudioPairingState.Associated(updated)
            } else {
                logState("BONDING", source = "restore")
                _state.value = HeyCyanAudioPairingState.Bonding(updated)
            }
            reconcileRoute(routeMonitor.routes.value, "restore")
        }
    }

    fun requestAssociation(wearableDeviceId: String) {
        activeWearableId = wearableDeviceId
        val currentState = _state.value

        // Idempotency check: don't launch CDM dialog if already in progress or connected
        when (currentState) {
            HeyCyanAudioPairingState.Discovering,
            HeyCyanAudioPairingState.AwaitingUserConfirmation,
            is HeyCyanAudioPairingState.Bonding -> {
                logState(currentState::class.simpleName ?: "IN_PROGRESS", reason = "Association already in progress", source = "request")
                return
            }
            is HeyCyanAudioPairingState.Associated,
            is HeyCyanAudioPairingState.AudioConnected -> {
                scope.launch {
                    val currentAssoc = association
                    if (currentAssoc != null && associationStillExists(currentAssoc)) {
                        logState("ASSOCIATED", reason = "Existing valid association refreshed", source = "request")
                        reconcileRoute(routeMonitor.routes.value, "request")
                        return@launch
                    }
                    startCdmAssociation(wearableDeviceId)
                }
                return
            }
            else -> Unit
        }

        scope.launch {
            // Check if Android already has a matching system association
            val saved = repository.read(wearableDeviceId)
            val matchingSystemAssoc = findMatchingSystemAssociation(saved?.bluetoothAddress)
            if (matchingSystemAssoc != null) {
                val address = matchingSystemAssoc.second ?: saved?.bluetoothAddress
                val assocId = matchingSystemAssoc.first
                val device = address?.let(::remoteDeviceOrNull)
                val isBonded = device != null && hasConnectPermission() && device.bondState == BluetoothDevice.BOND_BONDED
                val recovered = WearableAudioAssociation(
                    wearableDeviceId = wearableDeviceId,
                    associationId = assocId,
                    bluetoothAddress = address,
                    deviceName = if (device != null && hasConnectPermission()) device.name else "HeyCyan",
                    bonded = isBonded,
                    lastSeenAudioType = null
                )
                adoptAssociationInternal(recovered, source = "restore_cdm")
                return@launch
            }

            startCdmAssociation(wearableDeviceId)
        }
    }

    private fun startCdmAssociation(wearableDeviceId: String) {
        logState("DISCOVERING", source = "request")
        _state.value = HeyCyanAudioPairingState.Discovering
        val filter = BluetoothDeviceFilter.Builder()
            .setNamePattern(Pattern.compile("(?i).*(HeyCyan|BW[-_ ]?AG1).*"))
            .build()
        val request = AssociationRequest.Builder()
            .addDeviceFilter(filter)
            .setSingleDevice(false)
            .build()
        companionManager.associate(request, object : CompanionDeviceManager.Callback() {
            override fun onAssociationPending(intentSender: IntentSender) {
                logState("AWAITING_USER_CONFIRMATION", source = "callback")
                _state.value = HeyCyanAudioPairingState.AwaitingUserConfirmation
                _confirmationRequests.tryEmit(intentSender)
            }

            @Suppress("DEPRECATION")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                logState("AWAITING_USER_CONFIRMATION", source = "callback")
                _state.value = HeyCyanAudioPairingState.AwaitingUserConfirmation
                _confirmationRequests.tryEmit(chooserLauncher)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    scope.launch { acceptAssociation(associationInfo, source = "callback") }
                }
            }

            override fun onFailure(error: CharSequence?) {
                val msg = error?.toString() ?: "Audio association failed"
                logState("FAILED", reason = msg, source = "callback")
                _state.value = HeyCyanAudioPairingState.Failed(msg)
            }
        }, null)
    }

    fun fail(message: String) {
        logState("FAILED", reason = message, source = "fail")
        _state.value = HeyCyanAudioPairingState.Failed(message)
    }

    fun onConfirmationResult(resultCode: Int, data: Intent? = null) {
        if (resultCode != Activity.RESULT_OK) {
            logState("FAILED", reason = "Audio association was not approved", source = "activity_result")
            _state.value = HeyCyanAudioPairingState.Failed("Audio association was not approved")
        } else if (Build.VERSION.SDK_INT >= 33) {
            val assocInfo = data?.getParcelableExtra(CompanionDeviceManager.EXTRA_ASSOCIATION, AssociationInfo::class.java)
            if (assocInfo != null) {
                scope.launch { acceptAssociation(assocInfo, source = "activity_result") }
            } else {
                @Suppress("DEPRECATION")
                val device = data?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
                if (device != null) {
                    scope.launch { acceptLegacyDevice(device, source = "activity_result") }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            val device = data?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
            if (device == null) {
                logState("FAILED", reason = "Approved audio device was not returned", source = "activity_result")
                _state.value = HeyCyanAudioPairingState.Failed("Approved audio device was not returned")
            } else {
                scope.launch { acceptLegacyDevice(device, source = "activity_result") }
            }
        }
    }

    fun endpointFor(wearableDeviceId: String): BluetoothAudioEndpoint? {
        val saved = association?.takeIf { it.wearableDeviceId == wearableDeviceId } ?: return null
        return routeMonitor.routes.value.firstOrNull { endpointMatches(saved, it) }
    }

    fun matchesEndpoint(wearableDeviceId: String, endpoint: BluetoothAudioEndpoint): Boolean {
        val saved = association?.takeIf { it.wearableDeviceId == wearableDeviceId } ?: return false
        return endpointMatches(saved, endpoint)
    }

    fun resetAssociation(wearableDeviceId: String) {
        scope.launch {
            val saved = association
            if (saved != null) {
                cleanupDuplicateCdmAssociations(saved.bluetoothAddress, keepAssociationId = null)
            }
            association = null
            repository.write(WearableAudioAssociation(wearableDeviceId, null, null, null, false, null))
            logState("NOT_ASSOCIATED", reason = "User reset association", source = "reset")
            _state.value = HeyCyanAudioPairingState.NotAssociated
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private suspend fun acceptAssociation(info: AssociationInfo, source: String) {
        val wearableId = activeWearableId ?: return
        val address = info.deviceMacAddress?.toString()
        val device = address?.let(::remoteDeviceOrNull)
        val canInspectDevice = device != null && hasConnectPermission()
        val newAssoc = WearableAudioAssociation(
            wearableDeviceId = wearableId,
            associationId = info.id,
            bluetoothAddress = address,
            deviceName = if (canInspectDevice) device?.name else info.displayName?.toString(),
            bonded = canInspectDevice && device?.bondState == BluetoothDevice.BOND_BONDED,
            lastSeenAudioType = null
        )
        adoptAssociationInternal(newAssoc, source = source)
    }

    private suspend fun acceptLegacyDevice(device: BluetoothDevice, source: String) {
        val wearableId = activeWearableId ?: return
        if (!hasConnectPermission()) {
            logState("FAILED", reason = "Bluetooth connect permission is required", source = source)
            _state.value = HeyCyanAudioPairingState.Failed("Bluetooth connect permission is required")
            return
        }
        val newAssoc = WearableAudioAssociation(
            wearableDeviceId = wearableId,
            associationId = null,
            bluetoothAddress = device.address,
            deviceName = device.name,
            bonded = device.bondState == BluetoothDevice.BOND_BONDED,
            lastSeenAudioType = null
        )
        adoptAssociationInternal(newAssoc, source = source)
    }

    /** Idempotent adoption method guarded by mutex and address/ID deduplication. */
    @SuppressLint("MissingPermission")
    private suspend fun adoptAssociationInternal(newAssoc: WearableAudioAssociation, source: String) = adoptMutex.withLock {
        val existing = association
        if (existing != null &&
            (existing.associationId == newAssoc.associationId ||
                (existing.bluetoothAddress != null && sameAddress(existing.bluetoothAddress, newAssoc.bluetoothAddress))) &&
            _state.value !is HeyCyanAudioPairingState.NotAssociated &&
            _state.value !is HeyCyanAudioPairingState.Failed
        ) {
            logState(_state.value::class.simpleName ?: "ASSOCIATED", reason = "Duplicate adoption ignored", source = source)
            return@withLock
        }

        association = newAssoc
        repository.write(newAssoc)

        // Clean up stale duplicate CDM associations for the same MAC
        cleanupDuplicateCdmAssociations(newAssoc.bluetoothAddress, keepAssociationId = newAssoc.associationId)

        val device = newAssoc.bluetoothAddress?.let(::remoteDeviceOrNull)
        val canInspect = device != null && hasConnectPermission()
        val isBonded = canInspect && device?.bondState == BluetoothDevice.BOND_BONDED

        if (isBonded) {
            val updated = newAssoc.copy(bonded = true)
            association = updated
            repository.write(updated)
            logState("ASSOCIATED", source = source)
            _state.value = HeyCyanAudioPairingState.Associated(updated)
            reconcileRoute(routeMonitor.routes.value, source)
        } else if (canInspect) {
            logState("BONDING", source = source)
            _state.value = HeyCyanAudioPairingState.Bonding(newAssoc)
            if (!device.createBond()) {
                logState("FAILED", reason = "Could not start audio bonding", source = source)
                _state.value = HeyCyanAudioPairingState.Failed("Could not start audio bonding")
            }
        } else if (!hasConnectPermission()) {
            logState("FAILED", reason = "Bluetooth connect permission is required", source = source)
            _state.value = HeyCyanAudioPairingState.Failed("Bluetooth connect permission is required")
        } else {
            logState("ASSOCIATED", source = source)
            _state.value = HeyCyanAudioPairingState.Associated(newAssoc)
        }
    }

    private suspend fun updateBonded(current: WearableAudioAssociation, bonded: Boolean, source: String) {
        val updated = current.copy(bonded = bonded)
        association = updated
        repository.write(updated)
        logState("ASSOCIATED", source = source)
        _state.value = HeyCyanAudioPairingState.Associated(updated)
        reconcileRoute(routeMonitor.routes.value, source)
    }

    private suspend fun reconcileRoute(routes: List<BluetoothAudioEndpoint>, source: String) {
        val saved = association ?: return
        val endpoint = routes.firstOrNull { endpointMatches(saved, it) }
        if (endpoint != null) {
            val updated = saved.copy(lastSeenAudioType = endpoint.type.ordinal)
            association = updated
            repository.write(updated)
            logState("AUDIO_CONNECTED", source = source)
            _state.value = HeyCyanAudioPairingState.AudioConnected(updated, endpoint)
        } else if (_state.value is HeyCyanAudioPairingState.AudioConnected) {
            logState("ASSOCIATED", source = source)
            _state.value = HeyCyanAudioPairingState.Associated(saved)
        }
    }

    @SuppressLint("MissingPermission")
    private fun findMatchingSystemAssociation(bluetoothAddress: String?): Pair<Int?, String?>? {
        if (Build.VERSION.SDK_INT >= 33) {
            val myAssocs = companionManager.myAssociations
            if (myAssocs.isEmpty()) return null
            val match = myAssocs.firstOrNull { info ->
                bluetoothAddress != null && sameAddress(info.deviceMacAddress?.toString(), bluetoothAddress)
            } ?: myAssocs.firstOrNull { info ->
                val name = info.displayName?.toString().orEmpty()
                name.contains("HeyCyan", ignoreCase = true) || name.contains("BW-AG1", ignoreCase = true) || name.contains("BW_AG1", ignoreCase = true)
            }
            if (match != null) return Pair(match.id, match.deviceMacAddress?.toString() ?: bluetoothAddress)
        } else {
            @Suppress("DEPRECATION")
            val myAssocs = companionManager.associations
            val match = myAssocs.firstOrNull { addr ->
                bluetoothAddress != null && sameAddress(addr, bluetoothAddress)
            }
            if (match != null) return Pair(null, match)
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun cleanupDuplicateCdmAssociations(bluetoothAddress: String?, keepAssociationId: Int?) {
        if (bluetoothAddress.isNullOrBlank()) return
        if (Build.VERSION.SDK_INT >= 33) {
            val duplicates = companionManager.myAssociations.filter { info ->
                sameAddress(info.deviceMacAddress?.toString(), bluetoothAddress) &&
                    (keepAssociationId == null || info.id != keepAssociationId)
            }
            for (stale in duplicates) {
                runCatching { companionManager.disassociate(stale.id) }
                Log.i("HaloAudio", "Cleaned up duplicate CDM association id=${stale.id}")
            }
        } else {
            @Suppress("DEPRECATION")
            val duplicates = companionManager.associations.filter { sameAddress(it, bluetoothAddress) }
            for (stale in duplicates) {
                @Suppress("DEPRECATION")
                runCatching { companionManager.disassociate(stale) }
                Log.i("HaloAudio", "Cleaned up duplicate CDM legacy association addr=$stale")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun associationStillExists(saved: WearableAudioAssociation): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> {
            val myAssocs = companionManager.myAssociations
            if (saved.associationId != null) myAssocs.any { it.id == saved.associationId }
            else myAssocs.any { sameAddress(it.deviceMacAddress?.toString(), saved.bluetoothAddress) }
        }
        else -> {
            @Suppress("DEPRECATION")
            companionManager.associations.any { sameAddress(it, saved.bluetoothAddress) }
        }
    }

    private fun logState(stateName: String, reason: String? = null, source: String) {
        val currentAssoc = association
        val assocId = currentAssoc?.associationId
        val bonded = currentAssoc?.bonded ?: false
        val endpointType = (_state.value as? HeyCyanAudioPairingState.AudioConnected)?.endpoint?.type?.name
        val reasonPart = if (reason != null) " reason=\"$reason\"" else ""
        val endpointPart = if (endpointType != null) " endpointType=$endpointType" else ""
        Log.i("HaloAudio", "HALO_HEYCYAN_AUDIO_STATE state=$stateName associationId=${assocId ?: "none"} bonded=$bonded$endpointPart$reasonPart source=$source")
    }

    private fun endpointMatches(saved: WearableAudioAssociation, endpoint: BluetoothAudioEndpoint): Boolean =
        endpoint.connected && sameAddress(saved.bluetoothAddress, endpoint.address)

    @SuppressLint("MissingPermission")
    private fun remoteDeviceOrNull(address: String): BluetoothDevice? =
        runCatching { bluetoothAdapter?.getRemoteDevice(address) }.getOrNull()

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    private fun isDeviceBonded(device: BluetoothDevice?): Boolean =
        device != null && hasConnectPermission() && device.bondState == BluetoothDevice.BOND_BONDED

    private fun sameAddress(left: String?, right: String?): Boolean =
        left != null && right != null && left.equals(right, ignoreCase = true)
}
