package com.halo.sdk.runtime

import java.util.UUID

data class WakeWordDetected(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long) : HaloEvent
data class SpeechRecognized(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long, val text: String, val locale: String) : HaloEvent
data class IntentRecognized(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long, val intent: HaloIntent) : HaloEvent
data class AIResponseGenerated(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long, val response: String) : HaloEvent
data class DeviceCommand(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long, val command: String) : HaloEvent
data class TtsRequested(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long, val text: String) : HaloEvent

// Runtime Lifecycle Events
sealed interface RuntimeLifecycleEvent : HaloEvent
data class RuntimeBooting(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : RuntimeLifecycleEvent
data class RuntimeStarted(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : RuntimeLifecycleEvent
data class RuntimeStopping(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : RuntimeLifecycleEvent
data class RuntimeStopped(override val id: String = UUID.randomUUID().toString(), override val timestamp: Long = System.currentTimeMillis()) : RuntimeLifecycleEvent

// Engine Lifecycle Events
sealed interface EngineLifecycleEvent : HaloEvent {
    val engineId: String
}
data class EngineInitializing(override val id: String = UUID.randomUUID().toString(), override val engineId: String, override val timestamp: Long = System.currentTimeMillis()) : EngineLifecycleEvent
data class EngineStarted(override val id: String = UUID.randomUUID().toString(), override val engineId: String, override val timestamp: Long = System.currentTimeMillis()) : EngineLifecycleEvent
data class EngineFailed(override val id: String = UUID.randomUUID().toString(), override val engineId: String, val reason: String, override val timestamp: Long = System.currentTimeMillis()) : EngineLifecycleEvent
data class EngineStopped(override val id: String = UUID.randomUUID().toString(), override val engineId: String, override val timestamp: Long = System.currentTimeMillis()) : EngineLifecycleEvent

// Provider Events
data class ProviderConnected(override val id: String = UUID.randomUUID().toString(), val providerId: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent

// Planning & Execution Events
data class GoalCreated(override val id: String = UUID.randomUUID().toString(), val goalId: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class PlanGenerated(override val id: String = UUID.randomUUID().toString(), val planId: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class ExecutionStarted(override val id: String = UUID.randomUUID().toString(), val planId: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class ExecutionFinished(override val id: String = UUID.randomUUID().toString(), val planId: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
data class OutcomePublished(override val id: String = UUID.randomUUID().toString(), val outcome: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
