package com.halo.drivers.heycyan.ble

import com.halo.drivers.heycyan.protocol.HeyCyanProtocolConstants
import java.util.UUID

data class HeyCyanGattEndpoints(
    val commandWrite: UUID?,
    val notification: UUID?,
    val dataTransfer: UUID?,
    val uartTx: UUID?,
    val uartRx: UUID?,
)

class AmbiguousHeyCyanEndpointException(
    message: String
) : IllegalStateException(message)

class HeyCyanCharacteristicResolver {

    fun resolve(
        services: List<DiscoveredService>
    ): HeyCyanGattEndpoints {

        // First check for active Android AAR / Physical Capture service (de5bf728-d711-4e47-af26-65e3012a5dc7)
        val primary = services.firstOrNull {
            it.uuid == HeyCyanProtocolConstants.PRIMARY_SERVICE
        } ?: services.firstOrNull {
            it.uuid == HeyCyanProtocolConstants.ALTERNATE_IOS_PRIMARY_SERVICE
        }

        val secondary = services.firstOrNull {
            it.uuid == HeyCyanProtocolConstants.ALTERNATE_IOS_SECONDARY_SERVICE
        }

        if (primary != null && primary.uuid == HeyCyanProtocolConstants.PRIMARY_SERVICE) {
            // Direct resolution for verified active Android profile
            val writeChar = primary.characteristics.firstOrNull {
                it.uuid == HeyCyanProtocolConstants.WRITE_CHARACTERISTIC || it.properties.canWrite || it.properties.canWriteWithoutResponse
            }?.uuid

            val notifyChar = primary.characteristics.firstOrNull {
                it.uuid == HeyCyanProtocolConstants.NOTIFY_CHARACTERISTIC || it.properties.canNotify || it.properties.canIndicate
            }?.uuid

            return HeyCyanGattEndpoints(
                commandWrite = writeChar,
                notification = notifyChar,
                dataTransfer = null,
                uartTx = null,
                uartRx = null
            )
        }

        fun findSingle(
            characteristics: List<DiscoveredCharacteristic>,
            predicate: (DiscoveredCharacteristic) -> Boolean
        ): UUID? {
            val matches = characteristics.filter(predicate).map { it.uuid }
            if (matches.size > 1) {
                throw AmbiguousHeyCyanEndpointException(
                    "Multiple candidates for characteristic: ${matches.joinToString()}"
                )
            }
            return matches.firstOrNull()
        }

        val notificationCandidates = primary?.characteristics?.filter {
            it.properties.canNotify || it.properties.canIndicate
        } ?: emptyList()

        val notificationUuid = if (notificationCandidates.size > 1) {
            throw AmbiguousHeyCyanEndpointException(
                "Multiple notification candidates: ${notificationCandidates.map { it.uuid }.joinToString()}"
            )
        } else {
            notificationCandidates.firstOrNull()?.uuid
        }

        val dataCandidates = primary?.characteristics?.filter {
            it.properties.canNotify && it.uuid != notificationUuid
        } ?: emptyList()

        val dataTransferUuid = if (dataCandidates.size > 1) {
            throw AmbiguousHeyCyanEndpointException(
                "Multiple data transfer candidates: ${dataCandidates.map { it.uuid }.joinToString()}"
            )
        } else {
            dataCandidates.firstOrNull()?.uuid
        }

        return HeyCyanGattEndpoints(
            commandWrite = primary?.let { primaryService ->
                findSingle(
                    primaryService.characteristics,
                    { it.properties.canWrite || it.properties.canWriteWithoutResponse }
                )
            },

            notification = notificationUuid,

            dataTransfer = dataTransferUuid,

            uartTx = secondary?.let { secondaryService ->
                findSingle(
                    secondaryService.characteristics,
                    { it.properties.canWriteWithoutResponse }
                )
            },

            uartRx = secondary?.let { secondaryService ->
                findSingle(
                    secondaryService.characteristics,
                    { it.properties.canNotify }
                )
            },
        )
    }
}

data class DiscoveredService(
    val uuid: UUID,
    val characteristics: List<DiscoveredCharacteristic>
)

data class DiscoveredCharacteristic(
    val uuid: UUID,
    val properties: CharacteristicProperties
)

data class CharacteristicProperties(
    val canRead: Boolean,
    val canWrite: Boolean,
    val canWriteWithoutResponse: Boolean,
    val canNotify: Boolean,
    val canIndicate: Boolean
)