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
    val currentIntent: Intent?,
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

enum class VoiceSessionState {
    IDLE,
    LISTENING,
    RECOGNIZING,
    PROCESSING,
    GENERATING,
    SPEAKING,
    FINISHED
}

data class VoiceSession(
    val id: String,
    val state: VoiceSessionState
)

data class IntentEntity(
    val type: String,
    val value: String
)

data class Intent(
    val type: String,
    val confidence: Float,
    val entities: List<IntentEntity>,
    val priority: Int,
    val source: String,
    val pluginId: String?,
    val metadata: Map<String, String>
)

enum class CognitiveLoad {
    LOW,
    MEDIUM,
    HIGH
}

data class AttentionScore(
    val score: Int // 0 to 100
)
