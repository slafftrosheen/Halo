# HPS-800: Provider Contract

No provider gets special treatment. Every provider implements the same interface.

## Provider Interface
```kotlin
interface Provider {
    suspend fun chat()
    suspend fun stream()
    suspend fun vision()
    suspend fun embed()
    suspend fun metadata()
    fun cancel()
    suspend fun health()
}
```

## Provider Metadata
Every provider returns metadata allowing the Runtime to choose:
Latency, Context Window, Offline Capability, Streaming Support, Vision Support, Embeddings Support, Functions, Token Speed, Memory Usage.
