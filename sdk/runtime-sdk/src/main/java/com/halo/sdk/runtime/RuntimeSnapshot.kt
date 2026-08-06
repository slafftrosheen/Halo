package com.halo.sdk.runtime

import java.time.Duration
import java.time.Instant

data class RuntimeSnapshot(
    val runtimeState: RuntimeState,
    val speech: SpeechSessionState,
    val engines: List<EngineSnapshot>,
    val timeline: List<TimelineEvent>,
    val providers: List<ProviderSnapshot>,
    val blackboard: BlackboardSnapshot
)

data class TimelineEvent(
    val id: String,
    val title: String,
    val description: String,
    val timestamp: Instant,
    val status: String
)

data class ProviderSnapshot(
    val id: String,
    val name: String,
    val status: String
)

data class BlackboardSnapshot(
    val factCount: Int,
    val recentFacts: List<String>
)

data class EngineSnapshot(
    val id: String,
    val state: EngineState,
    val health: EngineHealth,
    val uptime: Duration,
    val eventsProcessed: Long,
    val lastActivity: Instant
)
