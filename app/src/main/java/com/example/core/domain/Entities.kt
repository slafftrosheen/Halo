package com.example.core.domain

data class User(
    val id: String,
    val name: String
)

data class DeviceCapabilities(
    val audioInput: Boolean,
    val audioOutput: Boolean,
    val buttons: Boolean,
    val camera: Boolean,
    val display: Boolean,
    val imu: Boolean,
    val vibration: Boolean,
    val firmwareVersion: String
)

data class Device(
    val id: String,
    val manufacturer: String,
    val model: String,
    val firmware: String,
    val connectionState: String,
    val capabilities: DeviceCapabilities,
    val battery: Int,
    val driverId: String,
    val lastSeen: Long
)

data class Provider(
    val name: String,
    val supportsStreaming: Boolean,
    val supportsVision: Boolean,
    val supportsEmbeddings: Boolean,
    val supportsFunctions: Boolean,
    val supportsReasoning: Boolean,
    val supportsImages: Boolean,
    val supportsAudio: Boolean,
    val latencyMs: Int,
    val contextWindow: Int,
    val cost: String,
    val privacy: String
)

data class Conversation(
    val id: String,
    val startTime: Long,
    val endTime: Long?,
    val participants: List<String>,
    val messages: List<String>,
    val providerId: String,
    val averageLatencyMs: Int,
    val summary: String?,
    val tags: List<String>
)

data class Plugin(
    val id: String,
    val name: String,
    val version: String,
    val state: String,
    val permissions: List<String>
)
