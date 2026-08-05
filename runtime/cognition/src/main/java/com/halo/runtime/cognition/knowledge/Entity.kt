package com.halo.runtime.cognition.knowledge

sealed interface Entity {
    val confidence: Float
}

data class PersonEntity(
    val name: String,
    override val confidence: Float
) : Entity

data class LocationEntity(
    val location: String,
    override val confidence: Float
) : Entity

data class TimeEntity(
    val expression: String,
    override val confidence: Float
) : Entity

data class ObjectEntity(
    val name: String,
    override val confidence: Float
) : Entity

data class ApplicationEntity(
    val name: String,
    override val confidence: Float
) : Entity

data class DeviceEntity(
    val name: String,
    override val confidence: Float
) : Entity
