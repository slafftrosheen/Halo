package com.halo.app.ui.screens.devices

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import android.content.res.Resources
import com.halo.app.language.resolveUiText

internal data class DeviceAgeCopy(val english: String, val count: Long? = null)

/** Display metadata from the saved device timestamp, never an interpretation of device text. */
internal fun deviceAgeCopy(elapsedMs: Long): DeviceAgeCopy {
    val minutes = elapsedMs.coerceAtLeast(0L) / 60_000L
    if (minutes == 0L) return DeviceAgeCopy("Just now")
    if (minutes < 60L) return DeviceAgeCopy("%1\$d min ago", minutes)
    val hours = minutes / 60L
    if (hours < 24L) return DeviceAgeCopy("%1\$d hr ago", hours)
    val days = hours / 24L
    return if (days == 1L) DeviceAgeCopy("Yesterday") else DeviceAgeCopy("%1\$d days ago", days)
}

@Composable
internal fun localizedDeviceSeenValue(device: GenericDeviceUiState): String =
    resolveDeviceSeenValue(LocalResources.current, device)

internal fun resolveDeviceSeenValue(resources: Resources, device: GenericDeviceUiState): String {
    if (device.isConnected) return resolveUiText(resources, "Now")
    val age = device.lastSeenElapsedMs ?: return device.lastSeen
    val copy = deviceAgeCopy(age)
    return copy.count?.let { resolveUiText(resources, copy.english, it) } ?: resolveUiText(resources, copy.english)
}
