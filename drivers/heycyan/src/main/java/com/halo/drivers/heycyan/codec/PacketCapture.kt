package com.halo.drivers.heycyan.codec

import java.util.UUID

enum class PacketDirection {
    TX, RX
}

enum class PacketTransport {
    BLE,
    RFCOMM
}

enum class CaptureGattStage {
    CONNECT,
    MTU,
    SERVICE_DISCOVERY,
    CCCD,
    READY,
    TEARDOWN
}

enum class CaptureStagePhase {
    STARTED,
    SUCCEEDED,
    FAILED
}

const val CURRENT_CAPTURE_FORMAT_VERSION = 3

data class BlePacket(
    val sequence: Long,
    val timestamp: Long,
    val direction: PacketDirection,
    val characteristic: UUID,
    val payload: ByteArray,
    val transport: PacketTransport = PacketTransport.BLE,
    val transportChannel: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BlePacket

        if (sequence != other.sequence) return false
        if (timestamp != other.timestamp) return false
        if (direction != other.direction) return false
        if (characteristic != other.characteristic) return false
        if (!payload.contentEquals(other.payload)) return false
        if (transport != other.transport) return false
        if (transportChannel != other.transportChannel) return false

        return true
    }

    override fun hashCode(): Int {
        var result = sequence.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + direction.hashCode()
        result = 31 * result + characteristic.hashCode()
        result = 31 * result + payload.contentHashCode()
        result = 31 * result + transport.hashCode()
        result = 31 * result + (transportChannel?.hashCode() ?: 0)
        return result
    }
}

data class DiscoveredCharacteristic(
    val uuid: UUID,
    val properties: Int,
    val descriptors: List<UUID>
)

/**
 * A complete transport-level capture session. Payloads are persisted as hex;
 * no protocol interpretation is performed here.
 */
data class BleCapture(
    val schemaVersion: Int = CURRENT_CAPTURE_FORMAT_VERSION,
    val captureId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val scenario: String = "discovery",
    val deviceAddress: String? = null,
    val deviceName: String? = null,
    val metadata: CaptureMetadata = CaptureMetadata(),
    val advertisements: List<CaptureAdvertisement> = emptyList(),
    val packets: List<BlePacket> = emptyList(),
    val services: List<CaptureServiceInfo> = emptyList(),
    val requestedMtu: Int? = null,
    val mtu: Int? = null,
    val timeline: List<CaptureEvent> = emptyList()
)

data class CaptureMetadata(
    val haloVersion: String? = null,
    val gitCommit: String? = null,
    val androidVersion: String? = null,
    val phoneModel: String? = null,
    val bluetoothAdapter: String? = null,
    val glassesFirmware: String? = null
)

data class CaptureAdvertisement(
    val timestamp: Long,
    val address: String,
    val name: String? = null,
    val rssi: Int,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: Map<String, String> = emptyMap()
)

data class CaptureServiceInfo(
    val uuid: String,
    val type: Int,
    val characteristics: List<CaptureCharacteristicInfo> = emptyList()
)

data class CaptureCharacteristicInfo(
    val uuid: String,
    val properties: Int,
    val permissions: Int,
    val descriptors: List<CaptureDescriptorInfo> = emptyList()
)

data class CaptureDescriptorInfo(
    val uuid: String,
    val permissions: Int
)

enum class CaptureEventKind {
    SESSION_STARTED,
    SESSION_FINISHED,
    ADVERTISEMENT,
    CONNECTION_STATE_CHANGED,
    GATT_STAGE,
    SERVICE_DISCOVERED,
    CHARACTERISTIC_DISCOVERED,
    DESCRIPTOR_DISCOVERED,
    DESCRIPTOR_WRITE,
    MTU_CHANGED,
    PACKET,
    READY,
    RFCOMM_CHANNEL_CONNECTED,
    RFCOMM_CHANNEL_DISCONNECTED
}

data class CaptureEvent(
    val sequence: Long,
    val timestamp: Long,
    val kind: CaptureEventKind,
    val serviceUuid: String? = null,
    val characteristicUuid: String? = null,
    val descriptorUuid: String? = null,
    val direction: PacketDirection? = null,
    val packetIndex: Int? = null,
    val advertisementIndex: Int? = null,
    val properties: Int? = null,
    val permissions: Int? = null,
    val status: Int? = null,
    val mtu: Int? = null,
    val newState: Int? = null,
    val transport: PacketTransport? = null,
    val transportChannel: String? = null,
    val attemptId: Long? = null,
    val gattStage: CaptureGattStage? = null,
    val stagePhase: CaptureStagePhase? = null,
    val detail: String? = null
)
