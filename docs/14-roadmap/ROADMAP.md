# Halo Execution Roadmap

Status: EXECUTING. Updated 2026-09-25.

See [NEXT-PHASE-2027.md](NEXT-PHASE-2027.md) for proposed batches and detailed acceptance criteria.

The runtime substrate sequence is now explicit. Each batch is a code-construction pass;
stabilization (compile/test/device/HIL) follows after the substrate exists.

1. ~~**RUNTIME-017 — Portable tool fabric + capability discovery**~~ — LANDED (2026-09-25).
   `ToolDescriptor`/security/execution policy, portable categories, descriptor-driven
   authorization, structured failure kinds + retry rules, `CapabilityCatalogue` + Android
   publisher, `ToolDiscoveryService` + `halo_discover_tools` bounded exposure. Present in
   source; not yet test- or hardware-validated.
2. ~~**RUNTIME-018 — Durable execution journal + recovery planner**~~ — LANDED (2026-09-25).
   Durable `ToolInvocation` identity with canonical arguments + digest + stable idempotency
   keys, append-only execution journal (`ProviderTurnStarted/Completed`,
   `ToolInvocationPrepared`, `ToolExecutionClaimed`, `ToolOutcomeRecorded`,
   `InputRequested/Resolved`, `ResponseCommitted`), `AgentCheckpointAssembler`,
   deterministic `AgentRecoveryPlanner` with `RecoveryExplanation`, reconciliation contract,
   durable approval/clarification requests, `CognitiveSession.resume(runId)` on the normal
   execution path, atomic `claimResume`, idempotent conversation commit, startup recovery
   classification (classify, never surprise-execute), routine `NO_RESUME` preservation.
   IMPLEMENTED / UNVALIDATED: not yet test- or hardware-validated.
3. ~~**RUNTIME-019 — Portable Interaction Surfaces**~~ — LANDED (2026-09-25).
   Runtime-owned semantic interaction protocol in `runtime/cognition/.../interaction/`:
   `InteractionSurface`/`InteractionContent`/`InteractionResponse` contracts (no Android types),
   validator with hard content limits, in-memory surface store projected from the durable journal,
   `AgentInputRequest` → surface factory, journal-derived progress/result projection with
   `ActionTruth`, atomic `resolveInputIfPending` response handler (first valid resolution wins),
   `RunResumptionCoordinator` → `CognitiveSession.resume`. Approvals and clarifications migrated
   onto surfaces (durable requests; digest-matched auto-reauthorization; no CompletableDeferred,
   no 120 s timeout); `ToolPresentation` descriptor metadata replaced the preview name switch;
   Android Compose renderer + presenter/ViewModel, SAF file adapter, voice adapter over the
   existing TTS path with origin-device affinity; ephemeral approval sheet removed.
   IMPLEMENTED / UNVALIDATED: not yet test- or hardware-validated.
4. ~~**RUNTIME-020 — Resource leases + capability affinity + multi-device arbitration**~~ — LANDED (2026-09-25).
   `ResourceLeaseManager`, `ResourceId`/`EndpointId`/`ResourceKey` identities, `ResourceClaim`
   (`SHARED`/`EXCLUSIVE`), priority + wait policies, cooperative yield protocol, StateFlow snapshots.
   `DeviceCapabilityResolver` typed matching + explicit `CapabilityAffinity` (ANY, ORIGIN, HOST,
   SPECIFIC_OWNER), `CapabilityResourceBinding` + `ResourcePlanner`. `ToolResourceCoordinator` +
   `CognitiveSession` pre-execution resource acquisition, journaled blockage (`ResourceBlocked`),
   `WAITING_FOR_RESOURCE` state. Audio session + native compute bridged onto generic resource leases;
   `LeasedStillImageSource` + `LeasedWirelessTransferCapability` generic wrappers; `HeyCyanResourceArbiter`
   preserved inside driver. IMPLEMENTED / UNVALIDATED.
5. **RUNTIME-021 — Provider workload/capability routing + multimodal context acquisition**
6. **STABILIZATION** — compile/test/device/HIL; fix the integrated foundation together

HeyCyan validation remains an essential engineering track, but it is not the entire roadmap. 
All future batches must adhere to the product contract and interaction-origin continuity rules established in the [Vision](../01-vision/VISION.md).
Runtime expresses capabilities, resources, context, tools, interactions and execution; hosts and
drivers translate those abstractions into Android/Linux/Windows/wearable-specific behavior.
