# REFERENCE-MINING-RECOVERY-002 — 2026-10-10

Follow-up mining OneBox's persisted agent tool calls, OGAM's explicit unknown tool outcomes, and OpenDroid's failed-step stop semantics. No reference source code was copied.

## Implemented
- Recovery now claims its resume ownership through the existing atomic AgentRunStore/Room transaction before observing external UI or writing any synthetic outcome. Losing surfaces return without touching the journal.
- Claim fingerprint uses the last meaningful journal evidence, not an existing recovery-claim row. A first winning resume keeps its original plan; after new UI reconciliation evidence the planner reevaluates.
- Historical ToolCompleted and ToolFailed evidence prevents synthesized duplicate UI outcomes.
- The planner rejects claimed actions whose prepared invocation, idempotency key, tool identity or persisted replay policy is absent or inconsistent. Recorded outcomes must postdate the claim and match the durable invocation ID, not only a provider-generated tool-call ID. It preserves exact interaction ID and idempotency key for safely replayable reads.
- ToolCallResolution prioritizes ActionTruth.UNKNOWN over the historical success boolean. Recovery blocks uncertain actions instead of continuing a multi-step plan as though external state were verified.

## Compatibility and safety
No additional dispatcher, host permissions, provider, model, Room schema, or Android dependency. NO_RESUME routines stay excluded. No automatic external side-effect replay is enabled.

## Validation status
Eleven portable JVM regression tests added for safe replay identity, legacy completion, conflicting outcome truth, orphan/mismatched claims, repeated recovery evidence, provider-call ID reuse, and two-surface recovery admission. They have **not** been executed in this connector session. Full Gradle build, instrumentation and device acceptance remain open. GitHub Actions were neither edited nor run.

## Device acceptance
Kill Halo after preparing then claiming an external action. Resume via two surfaces simultaneously and verify exactly one claim; the loser must not take a screenshot or journal a result. Verify old ToolCompleted rows, externally unknown calls, existing approval requests, read-only retries and NO_RESUME routines; no duplicate call/message/alarm execution.
