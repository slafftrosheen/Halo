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

        fun findCharacteristic(
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

        return HeyCyanGattEndpoints(
            commandWrite = primary?.let { primaryService ->
                findCharacteristic(
                    primaryService.characteristics,
                    { it.properties.canWrite }
                )
            },

            notification = primary?.let { primaryService ->
                findCharacteristic(
                    primaryService.characteristics,
                    { it.properties.canNotify || it.properties.canIndicate }
                )
            },

            dataTransfer = primary?.let { primaryService ->
                findCharacteristic(
                    primaryService.characteristics,
                    { 
                        it.properties.canNotify && 
                        it.properties.canNotify &&
                        // Exclude the notification characteristic itself
                        it.uuid != findCharacteristic(
                            primaryService.characteristics,
                            { it.properties.canNotify || it.properties.canIndicate }
                        )
                    }
                )
            },

            uartTx = secondary?.let { secondaryService ->
                findCharacteristic(
                    secondaryService.characteristics,
                    { it.properties.canWriteWithoutResponse }
                )
            },

            uartRx = secondary?.let { secondaryService ->
                findCharacteristic(
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