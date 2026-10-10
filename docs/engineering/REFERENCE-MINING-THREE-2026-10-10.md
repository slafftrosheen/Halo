# Three-reference runtime harvest — 2026-10-10

Status: source changes committed directly to main. A standalone Kotlin compilation and smoke probe of the budgeter passed locally; full Gradle, instrumentation and device acceptance were not run in this pass. No GitHub Actions workflow was edited or manually started.

## Sources inspected

- OneBox: feature/ai/src/main/java/com/shifenmiao/ai/context/ToolResultTruncator.kt and feature/ai/src/main/java/com/shifenmiao/ai/agent/AgentLoopExecutor.kt at wangzhishou/OneBox main (2026-10-10).
- OGAM: src/services/tools/toolResult.ts and src/services/generationToolLoop.ts at off-grid-ai/OGAM main (2026-10-10).
- OpenDroid: app/src/main/java/com/opendroid/ai/core/agent/ActionSequenceExecutor.kt and ReEvaluationEngine.kt at yashab-cyber/opendroid main (2026-10-10).

No external code, assets or third-party dependencies were copied.

## Wired in this pass

1. Runtime tool result budgeting retains a bounded prefix and suffix instead of dropping all trailing content. Its truncation marker states the exact omitted character count and that the result is incomplete. Multi-line output prefers natural line boundaries; cuts cannot split UTF-16 surrogate pairs. Original UTF-8 byte size and character count still describe the source result. OneBox's truncation principle is adapted to Halo's ToolRegistry and payload store.
2. Runtime tool-result envelopes accept an already-formatted success envelope only when its machine-readable status, reason code and action truth match the runtime-supplied values. A tool/MCP-originated fake header cannot silently claim CONFIRMED when Halo recorded DISPATCHED. Mismatched output becomes unverified nested detail with the authoritative header first. This applies OGAM's typed-result principle without a competing result type.
3. Empty successful tool outputs explicitly say content_status=empty. Execution truth is unchanged: no content is not proof of failure; DISPATCHED is not externally confirmed completion.
4. JVM regression tests cover long logs, JSON incompleteness, small hard caps, surrogate pairs, original sizes, failure preservation, fake tool headers and empty success.

## Already owned by Halo; intentionally not duplicated

- OneBox: iteration limits, dynamic tools, permissions and approval are already handled by ToolLoopLimits, AgentRunGuard, ToolRegistry and durable approval records.
- OGAM: runaway prevention and untrusted oversized read-only payload storage already use ToolRegistry/ContentPayloadStore; LiteRT downloads and qualification have their own owners.
- OpenDroid: step truth, non-replayable side effects, plan continuation and clarification use ToolTurnExecutor, AgentRunCoordinator, journals and ReplaySafety. A second action dispatcher would bypass them.

## Follow-up candidates requiring their own design and acceptance

- OneBox: assess read-only parallel-tool execution. Never parallelize user prompts, side effects, exclusive resources or shared device state.
- OGAM: better resident-model memory pressure and unload/recovery UX through the native resource coordinator; no GGUF stack or bypass of LiteRT qualification.
- OpenDroid: better phone-side result reconciliation with clear failed vs dispatched vs externally verified states; no arbitrary personal-data scraping or automatic accessibility grants.
- Device gate: run runtime/cognition unit tests and full app build; test long MCP logs, long JSON, empty results and forged tool headers. Verify EN/RU/LV presentation where surfaced.

## Architectural invariants

Never treat model text as action evidence, change ActionTruth through truncation, alter side-effect replay safety or exceed the declared output cap. Portable cognition must remain independent of Android and native provider internals.
