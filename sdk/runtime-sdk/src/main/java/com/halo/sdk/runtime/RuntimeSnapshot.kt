package com.halo.sdk.runtime

import java.time.Duration
import java.time.Instant

data class RuntimeHealth(
    val healthy: Boolean,
    val uptime: Duration,
    val degraded: Boolean,
    val lastFailure: Instant?
)

data class RuntimeSnapshot(
    val runtimeVersion: String,
    val buildNumber: Int,
    val startedAt: Instant,
    val health: RuntimeHealth,
    val runtimeState: RuntimeState,
    val speech: SpeechSessionState,
    val engines: List<EngineSnapshot>,
    val timeline: List<TimelineEvent>,
    val providers: List<ProviderSnapshot>,
    val blackboard: BlackboardSnapshot,
    val traces: List<ObservationTrace> = emptyList()
)

data class StageMetrics(
    val stage: RuntimeStage,
    val startedAt: Long,
    val finishedAt: Long,
    val latency: Long,
    val success: Boolean,
    val error: String? = null
)

enum class TraceState {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

enum class TraceValidation {
    VALID,
    INVALID,
    PENDING
}

data class ObservationTrace(
    val observationId: String,
    val startedAt: Long,
    val finishedAt: Long,
    val totalLatency: Long,
    val state: TraceState,
    val validation: TraceValidation,
    val stages: List<StageMetrics>
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

data class FactSummary(
    val id: String,
    val type: String,
    val createdAt: Instant
)

data class BlackboardSnapshot(
    val factCount: Int,
    val recentFacts: List<FactSummary>
)

data class EngineMetrics(
    val latencyMs: Long?,
    val uptime: Duration,
    val eventsProcessed: Long,
    val queueSize: Int,
    val cpuUsage: Double
)

data class EngineSnapshot(
    val id: String,
    val state: EngineState,
    val health: EngineHealth,
    val metrics: EngineMetrics,
    val lastActivity: Instant
)
