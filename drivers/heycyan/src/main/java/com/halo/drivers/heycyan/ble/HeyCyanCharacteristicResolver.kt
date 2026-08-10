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

        val primary = services.firstOrNull {
            it.uuid == HeyCyanProtocolConstants.PRIMARY_SERVICE
        }

        val secondary = services.firstOrNull {
            it.uuid == HeyCyanProtocolConstants.SECONDARY_SERVICE
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
                    { it.properties.canWrite }
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