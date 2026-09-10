package com.halo.app.ui.shell

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import com.halo.app.ui.glass.components.HaloGlassBackHeader
import com.halo.app.ui.glass.components.HaloGlassChip
import com.halo.app.ui.glass.components.HaloGlassDivider
import com.halo.app.ui.glass.components.HaloGlassPage
import com.halo.app.ui.glass.components.HaloGlassSection
import com.halo.app.ui.glass.material.HaloGlassColors
import com.halo.app.ui.glass.material.HaloTypography

/**
 * Runtime-facing permission status. The onboarding permission gate remains in
 * MainActivity; this screen is read-only and shows the host permissions that
 * Halo currently depends on.
 */
@Composable
fun PermissionsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val api31Plus = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
    val micGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED
    val bluetoothGranted = if (api31Plus) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }
    val locationGranted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val nearbyWifiGranted = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.NEARBY_WIFI_DEVICES
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    HaloGlassPage(title = "") {
        HaloGlassBackHeader(title = "Permissions", onBack = onBack)
        Spacer(Modifier.height(8.dp))
        Text(
            "Permissions used by the Android host",
            style = HaloTypography.bodyMedium,
            color = HaloGlassColors.TextSecondary
        )

        HaloGlassSection(title = "AUDIO") {
            PermissionItem(Icons.Default.Mic, "Microphone", "Voice capture and speech recognition", micGranted)
            HaloGlassDivider()
            PermissionItem(Icons.Default.VolumeUp, "Speaker", "Response audio", true)
        }

        HaloGlassSection(title = "WEARABLE ACCESS") {
            PermissionItem(
                Icons.Default.Security,
                if (api31Plus) "Bluetooth LE" else "Bluetooth / Location",
                "Pairing, connection and wearable control",
                bluetoothGranted
            )
            if (api31Plus && locationGranted) {
                HaloGlassDivider()
                PermissionItem(
                    Icons.Default.Security,
                    "Location",
                    "Granted; not the primary BLE permission on Android 12+",
                    true
                )
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                HaloGlassDivider()
                PermissionItem(
                    Icons.Default.Security,
                    "Nearby Wi-Fi",
                    "Required by Wi-Fi transfer features when used",
                    nearbyWifiGranted
                )
            }
        }

        HaloGlassSection(title = "HOST FEATURES") {
            CapabilityLine("Speech recognition", true)
            HaloGlassDivider()
            CapabilityLine("Text-to-speech", true)
            HaloGlassDivider()
            CapabilityLine("Camera input", false)
        }
    }
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    description: String,
    granted: Boolean
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.material3.Icon(
            icon,
            contentDescription = label,
            tint = HaloGlassColors.Accent,
            modifier = Modifier.size(20.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
            Text(description, style = HaloTypography.labelSmall, color = HaloGlassColors.TextMuted)
        }
        HaloGlassChip(text = if (granted) "Allowed" else "Not allowed")
    }
}

@Composable
private fun CapabilityLine(name: String, supported: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = HaloTypography.bodyMedium, color = HaloGlassColors.TextPrimary)
        if (supported) {
            Text("Supported", style = HaloTypography.labelSmall, color = HaloGlassColors.Success)
        } else {
            HaloGlassChip.Danger("Unavailable")
        }
    }
}
