package com.halo.api

/**
 * The internal HALO API DSL.
 * This provides a fluent, predictable surface for interacting with the Kernel.
 */
class HaloApi {
    val voice = VoiceApi()
    val memory = MemoryApi()
    val automation = AutomationApi()
    val provider = ProviderApi()
}

class VoiceApi {
    fun listen() { /* ... */ }
    fun speak(text: String) { /* ... */ }
}

class MemoryApi {
    fun search(query: String) { /* ... */ }
    fun store(data: Any) { /* ... */ }
}

class AutomationApi {
    fun create() { /* ... */ }
}

class ProviderApi {
    fun generate() { /* ... */ }
}

// Global accessor
val Runtime = HaloApi()
