package com.example.core.context

data class ProviderInfo(
    val id: String,
    val name: String
)

data class AssistantState(
    val listening: Boolean,
    val speaking: Boolean,
    val connected: Boolean,
    val provider: ProviderInfo,
    val currentIntent: String?,
    val battery: Int
)

data class ContextSnapshot(
    val timestamp: Long,
    val location: String?,
    val movement: String?,
    val batteryLevel: Int,
    val deviceState: String,
    val currentApp: String?,
    val activeNotifications: Int,
    val upcomingCalendarEvent: String?,
    val weather: String?,
    val connectivity: String,
    val mediaPlaying: Boolean,
    val focusMode: String
)

