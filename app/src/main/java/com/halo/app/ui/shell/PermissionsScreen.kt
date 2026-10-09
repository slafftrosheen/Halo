package com.halo.app.ui.shell

import com.halo.app.language.uiText

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.halo.app.ui.theme.HaloPastelMint
import com.halo.app.ui.theme.HaloPastelYellow
import com.halo.app.ui.workspace.HaloWorkspaceButton
import com.halo.app.ui.workspace.HaloWorkspaceCard
import com.halo.app.ui.workspace.HaloWorkspaceHeader
import com.halo.app.ui.workspace.HaloWorkspaceSection
import com.halo.app.backup.BackupLocationState
import com.halo.app.backup.StorageAccessRepository
import com.halo.app.di.CapabilityRefreshReason
import com.halo.app.di.ManualDependencyGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@Composable
fun PermissionsScreen(
    onRequestPermissions: () -> Unit,
    onBack: (() -> Unit)? = null,
    storageAccessRepository: StorageAccessRepository? = null,
    onOpenStorageBackup: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshKey by remember { mutableStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshKey++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @Suppress("UNUSED_VARIABLE")
    val ignored = refreshKey

    val micGranted = granted(Manifest.permission.RECORD_AUDIO)
    val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        granted(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val contactsGranted = granted(Manifest.permission.READ_CONTACTS)
    val permissionScope = androidx.compose.runtime.rememberCoroutineScope()
    val contactsPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) {
        refreshKey++
        permissionScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            CoroutineScope(Dispatchers.IO).launch {
                ManualDependencyGraph.hostGraph.capabilityPublisher.refresh(CapabilityRefreshReason.PERMISSION_RESULT)
            }
            ManualDependencyGraph.hostGraph.capabilityPublisher.publishInto(ManualDependencyGraph.capabilityCatalogue)
            com.halo.app.di.ManualDependencyGraph.skillRegistry.refreshAvailability()
        }
    }
    val localNetworkGranted = Build.VERSION.SDK_INT < 37 || granted(Manifest.permission.ACCESS_LOCAL_NETWORK)
    val networkPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { refreshKey++ }
    val wifiGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        granted(Manifest.permission.NEARBY_WIFI_DEVICES)
    val storageState = storageAccessRepository?.state?.collectAsStateWithLifecycle()?.value

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        HaloWorkspaceHeader(
            title = uiText("Permissions"),
            subtitle = "Only request access when a capability needs it.",
            onBack = onBack
        )

        Spacer(Modifier.height(12.dp))

        val allPermissionsLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            refreshKey++
            permissionScope.launch(Dispatchers.IO) {
                ManualDependencyGraph.hostGraph.capabilityPublisher.refresh(CapabilityRefreshReason.PERMISSION_RESULT)
                ManualDependencyGraph.hostGraph.capabilityPublisher.publishInto(ManualDependencyGraph.capabilityCatalogue)
                ManualDependencyGraph.skillRegistry.refreshAvailability()
            }
        }

        HaloWorkspaceButton(uiText("Grant all permissions for skills & tools"), onClick = {
            val permissionsToRequest = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                add(Manifest.permission.READ_CONTACTS)
                add(Manifest.permission.READ_CALENDAR)
                add(Manifest.permission.WRITE_CALENDAR)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                    add(Manifest.permission.BLUETOOTH_SCAN)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                    add(Manifest.permission.NEARBY_WIFI_DEVICES)
                }
            }.toTypedArray()
            allPermissionsLauncher.launch(permissionsToRequest)
        }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(20.dp))

        HaloWorkspaceCard(accent = if (micGranted) HaloPastelMint else HaloPastelYellow) {
            Text(
                text = if (micGranted) "Voice input can use the microphone" else "Microphone access is off",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = uiText("Halo remains usable without microphone access. Grant it only for voice input; Bluetooth and Nearby Wi-Fi are requested from the device flows that need them."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(20.dp))

        if (Build.VERSION.SDK_INT >= 37 && !localNetworkGranted) {
            HaloWorkspaceCard(accent = HaloPastelYellow) {
                Text(uiText("Local network access"), style = MaterialTheme.typography.titleMedium)
                Text(uiText("Allow access when connecting to nearby devices or a local AI server."))
                HaloWorkspaceButton(uiText("Allow local network access"), {
                    networkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                })
            }
        }
        if (!contactsGranted) {
            HaloWorkspaceButton(uiText("Allow contact lookup"), { contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS) })
        }
        HaloWorkspaceSection(title = uiText("Access")) {
            PermissionRow(Icons.Default.Mic, "Microphone", micGranted, missingLabel = "Needed for voice")
            PermissionRow(Icons.Default.Storage, "Contact names", contactsGranted, missingLabel = "Needed for contact lookup")
            if (Build.VERSION.SDK_INT >= 37) PermissionRow(Icons.Default.Wifi, "Local network", localNetworkGranted, missingLabel = "Needed for local servers and transfers")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PermissionRow(Icons.Default.Bluetooth, "Bluetooth", bluetoothGranted, missingLabel = "Requested from Devices")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                PermissionRow(Icons.Default.Wifi, "Nearby Wi-Fi", wifiGranted, missingLabel = "Requested for transfers")
            }
        }

        Spacer(Modifier.height(20.dp))
        HaloWorkspaceSection(title = uiText("Storage & Backup")) {
            PermissionDetailRow(Icons.Default.Storage, "Workspace storage", "Private on this phone")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PermissionDetailRow(
                Icons.Default.Storage,
                "Backup folder",
                when (storageState?.backupLocation) {
                    is BackupLocationState.Available -> "User-selected folder · Connected"
                    is BackupLocationState.PermissionLost -> "Access removed · Choose again"
                    is BackupLocationState.Unavailable -> "Unavailable"
                    else -> "Not selected"
                }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PermissionDetailRow(Icons.Default.Storage, "Imported files", "Selected files only")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PermissionDetailRow(Icons.Default.Storage, "Media library", "No broad access")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PermissionDetailRow(Icons.Default.Storage, "All files access", "Not requested")
        }
        if (onOpenStorageBackup != null) {
            Spacer(Modifier.height(12.dp))
            HaloWorkspaceButton(uiText("Manage Storage & Backup"), onOpenStorageBackup, Modifier.fillMaxWidth())
        }

        if (!micGranted) {
            Spacer(Modifier.height(20.dp))
            HaloWorkspaceButton(
                text = uiText("Grant microphone access"),
                onClick = {
                    onRequestPermissions()
                    refreshKey++
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PermissionDetailRow(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    missingLabel: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (granted) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (granted) "Granted" else missingLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
