# HeyCyan SDK Inventory

This document represents the forensic extraction inventory of the official vendor Android SDK.

## Artifact Identity
- **Original Source**: `glasses_sdk_20250723_v01.aar`
- **Package Name**: `com.glasses.sdk`
- **Minimum SDK**: 23
- **Target SDK**: 34
- **Date Code**: 20250723

## Structure Overview
- **`AndroidManifest.xml`**: Declares standard Bluetooth permissions (BLUETOOTH, BLUETOOTH_ADMIN, BLUETOOTH_CONNECT, BLUETOOTH_SCAN) and ANSWER_PHONE_CALLS. Contains no `<service>` or `<receiver>` tags, implying connection management is handled directly via normal `BluetoothGatt` instances rather than a persistent background Android Service.
- **`classes.jar`**: Main compiled codebase (~438KB).
- **`R.txt`**: Resource definitions (minimal).

## Missing Components (Architectural Findings)
- **NO NATIVE LIBRARIES**: The `.aar` does not contain a `jni/` or `libs/` folder. This means there are no compiled C/C++ `.so` files. **Result:** All protocol parsing, compression, cryptography, framing, CRC, and sequence logic is implemented in pure Java/Kotlin and exists directly within `classes.jar`.
- **NO THIRD PARTY DEPENDENCIES**: The AAR does not embed `okhttp`, `retrofit`, or `rxjava`. It operates over raw sockets, HTTP, and GATT.

## Internal Package Discovery (from decompilation)
- `com.oudmon.ble.base.*` - Core BLE framework
- `com.oudmon.ble.base.communication.*` - Protocol handling, codecs, commands
- `com.oudmon.qc_utils.*` - Utilities, byte conversions, Bluetooth utils
- `org.jvcompress.*` - Compression logic (e.g. LZO)

*(Inventory will be updated as extraction proceeds)*
