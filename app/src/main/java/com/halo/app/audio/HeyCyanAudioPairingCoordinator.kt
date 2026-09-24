package com.halo.app.audio

import android.Manifest
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
    private val bluetoothAdapter = appContext.getSystemService(BluetoothManager::class.java).adapter
    private val repository = WearableAudioAssociationRepository(storage, dispatchers)
    private val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
    private val _state = MutableStateFlow<HeyCyanAudioPairingState>(HeyCyanAudioPairingState.Unknown)
    val state: StateFlow<HeyCyanAudioPairingState> = _state.asStateFlow()
    private val _confirmationRequests = MutableSharedFlow<IntentSender>(extraBufferCapacity = 1)
    val confirmationRequests: SharedFlow<IntentSender> = _confirmationRequests.asSharedFlow()
    @Volatile private var activeWearableId: String? = null
    @Volatile private var association: WearableAudioAssociation? = null
    private val restoreMutex = Mutex()

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val current = association ?: return
            if (!sameAddress(current.bluetoothAddress, device.address)) return
            when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> scope.launch { updateBonded(current, true) }
                BluetoothDevice.BOND_NONE -> _state.value = HeyCyanAudioPairingState.Failed("Audio bond was removed")
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
            routeMonitor.routes.collectLatest { routes -> reconcileRoute(routes) }
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
        association = saved
        if (saved == null) {
            _state.value = HeyCyanAudioPairingState.NotAssociated
        } else if (!associationStillExists(saved)) {
            _state.value = HeyCyanAudioPairingState.Failed("Audio association needs repair")
        } else {
            _state.value = HeyCyanAudioPairingState.Associated(saved)
            reconcileRoute(routeMonitor.routes.value)
        }
    }

    fun requestAssociation(wearableDeviceId: String) {
        activeWearableId = wearableDeviceId
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
                _state.value = HeyCyanAudioPairingState.AwaitingUserConfirmation
                _confirmationRequests.tryEmit(intentSender)
            }

            @Suppress("DEPRECATION")
            override fun onDeviceFound(chooserLauncher: IntentSender) {
                _state.value = HeyCyanAudioPairingState.AwaitingUserConfirmation
                _confirmationRequests.tryEmit(chooserLauncher)
            }

            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                scope.launch { acceptAssociation(associationInfo) }
            }

            override fun onFailure(error: CharSequence?) {
                _state.value = HeyCyanAudioPairingState.Failed(error?.toString() ?: "Audio association failed")
            }
        }, null)
    }

    fun fail(message: String) {
        _state.value = HeyCyanAudioPairingState.Failed(message)
    }

    fun onConfirmationResult(resultCode: Int, data: Intent? = null) {
        if (resultCode != Activity.RESULT_OK) {
            _state.value = HeyCyanAudioPairingState.Failed("Audio association was not approved")
        } else if (Build.VERSION.SDK_INT < 33) {
            @Suppress("DEPRECATION")
            val device = data?.getParcelableExtra<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
            if (device == null) {
                _state.value = HeyCyanAudioPairingState.Failed("Approved audio device was not returned")
            } else {
                scope.launch { acceptLegacyDevice(device) }
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

    private suspend fun acceptAssociation(info: AssociationInfo) {
        val wearableId = activeWearableId ?: return
        val address = info.deviceMacAddress?.toString()
        val device = address?.let(::remoteDeviceOrNull)
        val canInspectDevice = device != null && hasConnectPermission()
        val saved = WearableAudioAssociation(
            wearableDeviceId = wearableId,
            associationId = info.id,
            bluetoothAddress = address,
            deviceName = if (canInspectDevice) device?.name else info.displayName?.toString(),
            bonded = canInspectDevice && device?.bondState == BluetoothDevice.BOND_BONDED,
            lastSeenAudioType = null
        )
        association = saved
        repository.write(saved)
        if (saved.bonded) {
            _state.value = HeyCyanAudioPairingState.Associated(saved)
        } else if (canInspectDevice) {
            _state.value = HeyCyanAudioPairingState.Bonding(saved)
            if (!device.createBond()) _state.value = HeyCyanAudioPairingState.Failed("Could not start audio bonding")
        } else {
            _state.value = HeyCyanAudioPairingState.Failed("Bluetooth connect permission is required")
        }
    }

    private suspend fun acceptLegacyDevice(device: BluetoothDevice) {
        val wearableId = activeWearableId ?: return
        if (!hasConnectPermission()) {
            _state.value = HeyCyanAudioPairingState.Failed("Bluetooth connect permission is required")
            return
        }
        val saved = WearableAudioAssociation(
            wearableDeviceId = wearableId,
            associationId = null,
            bluetoothAddress = device.address,
            deviceName = device.name,
            bonded = device.bondState == BluetoothDevice.BOND_BONDED,
            lastSeenAudioType = null
        )
        association = saved
        repository.write(saved)
        if (saved.bonded) {
            _state.value = HeyCyanAudioPairingState.Associated(saved)
        } else if (hasConnectPermission()) {
            _state.value = HeyCyanAudioPairingState.Bonding(saved)
            if (!device.createBond()) _state.value = HeyCyanAudioPairingState.Failed("Could not start audio bonding")
        }
    }

    private suspend fun updateBonded(current: WearableAudioAssociation, bonded: Boolean) {
        val updated = current.copy(bonded = bonded)
        association = updated
        repository.write(updated)
        _state.value = HeyCyanAudioPairingState.Associated(updated)
        reconcileRoute(routeMonitor.routes.value)
    }

    private suspend fun reconcileRoute(routes: List<BluetoothAudioEndpoint>) {
        val saved = association ?: return
        val endpoint = routes.firstOrNull { endpointMatches(saved, it) }
        if (endpoint != null) {
            val updated = saved.copy(lastSeenAudioType = endpoint.type.ordinal)
            association = updated
            repository.write(updated)
            _state.value = HeyCyanAudioPairingState.AudioConnected(updated, endpoint)
        } else if (_state.value is HeyCyanAudioPairingState.AudioConnected) {
            _state.value = HeyCyanAudioPairingState.Associated(saved)
        }
    }

    private fun associationStillExists(saved: WearableAudioAssociation): Boolean = when {
        Build.VERSION.SDK_INT >= 33 -> companionManager.myAssociations.any { it.id == saved.associationId }
        else -> {
            @Suppress("DEPRECATION")
            companionManager.associations.any { sameAddress(it, saved.bluetoothAddress) }
        }
    }

    private fun endpointMatches(saved: WearableAudioAssociation, endpoint: BluetoothAudioEndpoint): Boolean =
        endpoint.connected && sameAddress(saved.bluetoothAddress, endpoint.address)

    private fun remoteDeviceOrNull(address: String): BluetoothDevice? =
        runCatching { bluetoothAdapter.getRemoteDevice(address) }.getOrNull()

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

    private fun sameAddress(left: String?, right: String?): Boolean =
        left != null && right != null && left.equals(right, ignoreCase = true)
}
