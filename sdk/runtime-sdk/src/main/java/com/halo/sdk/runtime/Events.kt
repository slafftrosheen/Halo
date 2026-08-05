package com.halo.sdk.runtime

data class WakeWordDetected(override val timestamp: Long) : HaloEvent
data class SpeechRecognized(override val timestamp: Long, val text: String, val locale: String) : HaloEvent
data class IntentRecognized(override val timestamp: Long, val intent: HaloIntent) : HaloEvent
data class AIResponseGenerated(override val timestamp: Long, val response: String) : HaloEvent
data class DeviceCommand(override val timestamp: Long, val command: String) : HaloEvent
data class TtsRequested(override val timestamp: Long, val text: String) : HaloEvent

// Runtime Lifecycle Events
sealed interface RuntimeLifecycleEvent : HaloEvent
data class RuntimeBooting(override val timestamp: Long = System.currentTimeMillis()) : RuntimeLifecycleEvent
data class RuntimeStarted(override val timestamp: Long = System.currentTimeMillis()) : RuntimeLifecycleEvent
data class RuntimeStopping(override val timestamp: Long = System.currentTimeMillis()) : RuntimeLifecycleEvent
data class RuntimeStopped(override val timestamp: Long = System.currentTimeMillis()) : RuntimeLifecycleEvent

// Engine Lifecycle Events
sealed interface EngineLifecycleEvent : HaloEvent {
    val engineId: String
}
data class EngineInitializing(override val engineId: String, override val timestamp: Long = System.currentTimeMillis()) : EngineLifecycleEvent
data class EngineStarted(override val engineId: String, override val timestamp: Long = System.currentTimeMillis()) : EngineLifecycleEvent
data class EngineFailed(override val engineId: String, val reason: String, override val timestamp: Long = System.currentTimeMillis()) : EngineLifecycleEvent
data class EngineStopped(override val engineId: String, override val timestamp: Long = System.currentTimeMillis()) : EngineLifecycleEvent

// Provider Events
data class ProviderConnected(val providerId: String, override val timestamp: Long = System.currentTimeMillis()) : HaloEvent
