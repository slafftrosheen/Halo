package com.halo.drivers.heycyan.ble

/**
 * Classifies Android Bluetooth GATT status codes for reconnect policy.
 *
 * Android exposes several controller/stack errors that are transient from the
 * application's point of view. They must not automatically be treated as a
 * protocol failure or a proof that the selected device is unusable.
 */
internal object HeyCyanGattErrorClassifier {
    /**
     * 147 is commonly surfaced by Android as a transient GATT/controller error
     * during connection setup on some devices. The transport should retry with
     * a fresh BluetoothGatt instance rather than poisoning the session.
     */
    fun isTransient(status: Int): Boolean = status == 147

    fun describe(status: Int): String = when (status) {
        147 -> "TRANSIENT_GATT_147"
        else -> "GATT_STATUS_$status"
    }
}
