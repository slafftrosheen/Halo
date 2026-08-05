package com.halo.core.event

/**
 * Event Catalog - Definitions for the canonical system events
 */

// --- Voice Events ---
interface VoiceEvent
data class WakeWordDetected(val confidence: Float) : VoiceEvent
object SpeechStarted : VoiceEvent
object SpeechEnded : VoiceEvent
data class RecognitionCompleted(val text: String, val confidence: Float) : VoiceEvent
data class RecognitionFailed(val reason: String) : VoiceEvent
object TTSStarted : VoiceEvent
object TTSEnded : VoiceEvent

// --- Memory Events ---
interface MemoryEvent
data class MemoryCreated(val memoryId: String, val tags: List<String>) : MemoryEvent
data class MemoryDeleted(val memoryId: String) : MemoryEvent
data class MemoryUpdated(val memoryId: String) : MemoryEvent
data class MemorySearched(val query: String, val resultCount: Int) : MemoryEvent

// --- Provider Events ---
interface ProviderEvent
data class ProviderConnected(val providerId: String) : ProviderEvent
data class ProviderUnavailable(val providerId: String, val reason: String) : ProviderEvent
data class GenerationStarted(val providerId: String) : ProviderEvent
data class GenerationCompleted(val providerId: String, val tokens: Int) : ProviderEvent
data class StreamingStarted(val providerId: String) : ProviderEvent
