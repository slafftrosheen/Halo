package com.halo.app.ui.shell

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassButton
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

@Composable
fun PermissionsScreen(
    onBack: () -> Unit = {},
    onRequestPermissions: () -> Unit = {}
) {
    val context = LocalContext.current
    var refreshKey by remember { mutableStateOf(0) }

    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    // Read after refreshKey so the state is recomputed after returning from the platform prompt.
    @Suppress("UNUSED_VARIABLE")
    val ignored = refreshKey
    val micGranted = granted(Manifest.permission.RECORD_AUDIO)
    val bluetoothGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        granted(Manifest.permission.BLUETOOTH_SCAN) && granted(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        granted(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val wifiGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        granted(Manifest.permission.NEARBY_WIFI_DEVICES)
    val allGranted = micGranted && bluetoothGranted && wifiGranted

    HaloGlassPage(title = "") {
        HaloGlassBackHeader(title = "Permissions", onBack = onBack)

        HaloGlassSection(title = "STATUS") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    if (allGranted) "All required permissions granted" else "Permissions required",
                    style = HaloTypography.titleMedium,
                    color = HaloGlassColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Microphone is required for voice input. Bluetooth is required for wearable connectivity. Nearby Wi-Fi is used on Android 13+ for supported transfer flows.",
                    style = HaloTypography.bodySmall,
                    color = HaloGlassColors.TextSecondary
                )
            }
        }

        HaloGlassSection(title = "ACCESS") {
            PermissionRow(Icons.Default.Mic, "Microphone", micGranted)
            HaloGlassDivider()
            PermissionRow(Icons.Default.Bluetooth, "Bluetooth", bluetoothGranted)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                HaloGlassDivider()
                PermissionRow(Icons.Default.Wifi, "Nearby Wi-Fi", wifiGranted)
            }
        }

        if (!allGranted) {
            HaloGlassSection(title = "ACTION") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    HaloGlassButton(
                        text = "Grant Permissions",
                        onClick = {
                            onRequestPermissions()
                            refreshKey++
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    granted: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = HaloGlassColors.TextPrimary)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
            Text(
                if (granted) "Granted" else "Required",
                style = HaloTypography.bodySmall,
                color = HaloGlassColors.TextSecondary
            )
        }
    }
}
