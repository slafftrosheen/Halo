package com.halo.drivers.heycyan

import android.Manifest
import android.content.pm.PackageManager

internal fun shouldReadBluetoothAdapterName(
    sdkInt: Int,
    permissionChecker: (String) -> Int
): Boolean = sdkInt < 31 || permissionChecker(Manifest.permission.BLUETOOTH_CONNECT) ==
    PackageManager.PERMISSION_GRANTED
