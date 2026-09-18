---
title: HPS-8200-PROVIDER
status: Draft
maturity: Alpha
---

> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

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
