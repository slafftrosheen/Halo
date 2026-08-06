package com.halo.cognition.language

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

data class PlaceEntity(
    val name: String,
    override val confidence: Float
) : Entity

data class TimeEntity(
    val expression: String,
    override val confidence: Float
) : Entity

data class DateEntity(
    val expression: String,
    override val confidence: Float
) : Entity

data class NumberEntity(
    val value: Double,
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

data class MediaEntity(
    val title: String,
    override val confidence: Float
) : Entity

data class WebEntity(
    val query: String,
    override val confidence: Float
) : Entity
