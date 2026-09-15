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

    HaloGlassPage(title = "") {
        HaloGlassBackHeader(title = "Permissions", onBack = onBack)

        HaloGlassSection(title = "STATUS") {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                    if (micGranted) "Halo is ready for voice input" else "Microphone access is required",
                    style = HaloTypography.titleMedium,
                    color = HaloGlassColors.TextPrimary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Halo runs fully on your phone. Microphone access enables voice interaction. Bluetooth and Nearby Wi-Fi are optional and are only needed for external devices or supported transfer flows.",
                    style = HaloTypography.bodySmall,
                    color = HaloGlassColors.TextSecondary
                )
            }
        }

        HaloGlassSection(title = "ACCESS") {
            PermissionRow(Icons.Default.Mic, "Microphone", micGranted, required = true)
            HaloGlassDivider()
            PermissionRow(Icons.Default.Bluetooth, "Bluetooth", bluetoothGranted, required = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                HaloGlassDivider()
                PermissionRow(Icons.Default.Wifi, "Nearby Wi-Fi", wifiGranted, required = false)
            }
        }

        if (!micGranted) {
            HaloGlassSection(title = "ACTION") {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    HaloGlassButton(
                        text = "Grant Microphone Access",
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
    granted: Boolean,
    required: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = HaloGlassColors.TextPrimary)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = HaloTypography.bodyLarge, color = HaloGlassColors.TextPrimary)
            Text(
                when {
                    granted -> "Granted"
                    required -> "Required"
                    else -> "Optional"
                },
                style = HaloTypography.bodySmall,
                color = HaloGlassColors.TextSecondary
            )
        }
    }
}
