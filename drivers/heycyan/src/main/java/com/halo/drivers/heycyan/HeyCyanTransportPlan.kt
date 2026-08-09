package com.halo.drivers.heycyan

enum class HeyCyanTransport {
    RFCOMM,
    BLE_GATT
}

data class HeyCyanTransportPlan(
    val attemptOrder: List<HeyCyanTransport>,
    val gattRequired: Boolean
) {
    companion object {
        fun forMode(mode: HeyCyanConnectionMode): HeyCyanTransportPlan = when (mode) {
            HeyCyanConnectionMode.DISCOVERY -> HeyCyanTransportPlan(
                attemptOrder = listOf(HeyCyanTransport.RFCOMM, HeyCyanTransport.BLE_GATT),
                gattRequired = false
            )
            HeyCyanConnectionMode.RUNTIME -> HeyCyanTransportPlan(
                attemptOrder = listOf(HeyCyanTransport.RFCOMM, HeyCyanTransport.BLE_GATT),
                gattRequired = true
            )
        }
    }
}

internal fun isUsableDiscoveryTransport(
    rfcommChannels: List<String>,
    gattReady: Boolean
): Boolean = rfcommChannels.isNotEmpty() || gattReady
