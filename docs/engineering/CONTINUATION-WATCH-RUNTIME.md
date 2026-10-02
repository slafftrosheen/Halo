# Continuation Watch Runtime Specification

## Overview

The Continuation Watch Runtime manages durable external event watches tied to user follow-up requests or pending task continuation conditions.

```text
User / AgentRun -> ContinuationWatch -> AutomationFabric -> SignalSource -> ContinuationWatchMatcher -> ContinuationWatchRuntime -> Match Claim -> Attention / Run Resumption
```

## Watch Lifecycle & Statuses

1. **ACTIVE:** Watch is registered and waiting for matching signals or level conditions.
2. **MATCH_CLAIMED:** Match was detected and claimed transactionally in Room database.
3. **SATISFIED:** Watch behavior was executed (notification posted or run transitioned to resumable state).
4. **EXPIRED:** Watch expired before a matching condition occurred (`expiresAt <= now`).
5. **CANCELLED:** Watch was cancelled by user ("Cancel that watch") or when root run became terminal.

## Side-Effect Safety & Resume Policy

- **At-Most-Once Delivery:** Matching uses `claimWatchTransaction` (`claimState = 'CLAIMED'`).
- **No Side-Effect Replay:** Resuming a run after a watch matches never replays previously claimed external actions (`ActionTruth.DISPATCHED`).
- **Wake Policies:**
  - `SURFACE_TO_USER`: Surfaced via `AttentionController` (`AttentionSource.FollowUp`, default importance `NORMAL`).
  - `RECONCILE_ONLY`: Reconciles task state without user notification or external side effects.
- **Resource Contention:** Internal contention (`WAITING_FOR_RESOURCE`) is managed by `NativeResourceCoordinator` and does NOT create Room watches.
