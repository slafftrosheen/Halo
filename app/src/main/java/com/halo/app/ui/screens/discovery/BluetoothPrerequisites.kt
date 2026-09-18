package com.halo.app.ui.screens.discovery

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class BluetoothPrerequisite {
    READY,
    PERMISSION_REQUIRED,
    BLUETOOTH_OFF,
    UNSUPPORTED
}

fun requiredBluetoothDiscoveryPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

fun evaluateBluetoothPrerequisite(context: Context): BluetoothPrerequisite {
    val appContext = context.applicationContext
    val packageManager = appContext.packageManager
    if (!packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
        return BluetoothPrerequisite.UNSUPPORTED
    }

    val permissionsGranted = requiredBluetoothDiscoveryPermissions().all { permission ->
        ContextCompat.checkSelfPermission(appContext, permission) == PackageManager.PERMISSION_GRANTED
    }
    if (!permissionsGranted) return BluetoothPrerequisite.PERMISSION_REQUIRED

    val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        ?: return BluetoothPrerequisite.UNSUPPORTED
    val adapter = manager.adapter ?: return BluetoothPrerequisite.UNSUPPORTED

    return try {
        if (adapter.isEnabled) BluetoothPrerequisite.READY else BluetoothPrerequisite.BLUETOOTH_OFF
    } catch (_: SecurityException) {
        BluetoothPrerequisite.PERMISSION_REQUIRED
    }
}

fun BluetoothPrerequisite.userMessage(): String = when (this) {
    BluetoothPrerequisite.READY -> "Bluetooth is ready"
    BluetoothPrerequisite.PERMISSION_REQUIRED -> "Bluetooth access is required to discover glasses"
    BluetoothPrerequisite.BLUETOOTH_OFF -> "Bluetooth is turned off"
    BluetoothPrerequisite.UNSUPPORTED -> "Bluetooth Low Energy is not available on this device"
}
