# OneBox and Zafiro pattern mining

Status: ACTIVE IMPLEMENTATION NOTES. Updated 2026-09-27.

These archives are design references, not dependencies:

- `OneBox-main (1).zip`
- `zafiro-main.zip`

Halo keeps its own Runtime journal, interaction surfaces, tool policy, capability catalogue,
provider contracts and Android host boundary. Reference code must not create a parallel agent
runtime or move Android APIs into Runtime modules.

## Decision matrix

| Reference pattern | Halo decision | Halo owner |
|---|---|---|
| Zafiro bounded accessibility tree, semantic roles and opaque node indices | Adapt with snapshot-scoped opaque tokens, redaction and explicit truncation | `host-android` |
| Zafiro read-after-action cadence | Adopt in verified action batch; an action receipt alone is not success | Runtime tool execution + Android host |
| Zafiro foreground/background consent coordinators | Reuse Halo's durable `AgentInputRequest`; do not add another approval state machine | `runtime/cognition` |
| Zafiro phone-use skill | Adapt as behavioral guidance after semantic actions exist | future skill repository |
| Zafiro root/Shizuku/Xposed shell control | Reject as primary path; optional privileged adapter only | future host extension |
| Zafiro Python/terminal tools | Reject for default phone runtime pending a sandbox and explicit product requirement | none |
| OneBox skill frontmatter, validation and usage policy | Adapt into a versioned Halo skill manifest with provenance and bounded prompt contribution | future `runtime/skills` |
| OneBox conversation tool/memory policy | Adapt through existing Halo tool exposure and context policies | existing Runtime policy |
| OneBox durable tool-call task tables | Do not copy; Halo's append-only AgentStep journal remains execution truth | `runtime/cognition` |
| OneBox A2UI renderer registry and action bus | Mine renderer/versioning ideas; never accept arbitrary model UI as execution truth | interaction host renderers |

## RUNTIME-032 — semantic phone observation

Implemented in source, not device-validated:

- Accessibility capture remains on demand rather than event-streaming every screen change.
- Active package safety policy is checked before capture.
- Password nodes retain only a redacted semantic marker.
- Nodes receive portable semantic roles and opaque snapshot-scoped tokens.
- Capture has node, depth and character budgets with explicit truncation metadata.
- `halo_read_screen` exposes the projection as clearly delimited untrusted content.
- Capability truth distinguishes `host.screen.observe` from `host.screen.control`.
- Accessibility tools remain discoverable while disabled and report setup requirements at execution.
- Existing click behavior accepts semantic tokens first while legacy selectors remain temporarily supported.

Primary reference files inspected:

- Zafiro `agent-runtime/.../accessibility/TreeFormatter.kt`
- Zafiro `agent-runtime/.../accessibility/PruningRules.kt`
- Zafiro `app/src/main/assets/skills/phone-use/SKILL.md`
- Zafiro `ScreenControlConsent.kt` and `ToolPermissionCoordinator.kt`
- OneBox `core/a2ui/`
- OneBox `core/database/.../ai/SkillFrontMatterParser.kt`
- OneBox tool, memory-policy, skill and tool-call entities under `core/database/.../ai/`

## Next implementation gates

### RUNTIME-033 — verified semantic actions

Implemented in source, pending device validation:

1. Click and scroll require a recent snapshot identity and opaque token.
2. Text and view-id selectors were retired from model-visible schemas.
3. Scroll targets a specific semantic scroll container.
4. Actions capture a postcondition snapshot and record whether semantic change was observed.
5. `ActionTruth.CONFIRMED` is emitted only when a postcondition change is visible; otherwise truth remains `DISPATCHED`.
6. `ConfirmationPolicy.ALWAYS` can no longer be bypassed by a broad skill-level always-allow setting.

Remaining:

1. Classify enter-text, communicate and destructive screen operations separately before exposing them.
2. Never replay a claimed mutation blindly after process death.
3. Refuse password, authenticator, permission-controller, installer, banking and payment surfaces.

### RUNTIME-034 — Halo skill packages

Initial built-in slice implemented:

- `halo.phone` manifest binds observation, semantic actions, app enumeration and app launch.
- Accessibility setup participates in skill availability.
- Read-only tools derive safe default approval independently from mutating tools in the same skill.
- Phone-use guidance requires read-before-action, treats screen content as untrusted and forbids uncertain mutation replay.
- Semantic text entry is bounded, refuses password fields, redacts entered text from approval copy and is never replayable.
- Built-in manifests now have a size ceiling, schema validation, identifier validation and SHA-256 provenance.

Implemented package foundation:

- Strict bounded `SKILL.md` frontmatter and instruction parsing.
- Unknown/duplicate fields, invalid identifiers, schema mismatches and oversized packages are rejected.
- Imported skills are inert by default, SHA-256 attributed, and stored separately from bundled manifests.
- The Skills hub reads at most 64 KiB, previews risk/tools/permissions, and requires explicit install confirmation.
- Invalid installed packages remain excluded and produce inspectable repository issues.
- Custom instructions compose through the existing relevance-based context path.
- Registry refresh replaces only custom entries and preserves built-in ownership.
- `halo_use_skill` records skill identity, schema and content revision in its durable tool outcome.

### RUNTIME-035 — skill and tool hub

Present configuration and permission truth without making UI state execution truth. The Runtime
catalogue and journal remain authoritative.

Implemented in source:

- Existing runtime-backed Skills hub now exposes source, schema, content provenance, permissions,
  per-action availability and per-action risk.
- Skill policy remains an upper bound: tool descriptors that always require confirmation cannot be
  weakened by an always-allow skill setting.
- Screen observation and screen control have distinct human-readable capability requirements.

### RUNTIME-036 — verified phone workflows

Implemented in source, pending device validation:

- Every semantic mutation produces a post-action observation classified as package change,
  semantic content change, no observed change, or unobservable.
- Confirmed is reserved for observed semantic change; unchanged dispatch remains unproven.
- An unavailable postcondition produces `ActionTruth.UNKNOWN` rather than false success.
- Receipts direct the next step to a fresh screen read, or `STOP` when observation is unavailable.
- Snapshot-scoped tokens make the previous action vocabulary stale after postcondition capture,
  preventing a multi-step workflow from blindly chaining old selectors.

### RUNTIME-037 — bounded dynamic interaction context

Implemented in source:

- Interaction surface schema v2 adds only bounded, read-only facts, statuses and notices.
- The validator enforces count and text budgets.
- Supplements cannot introduce executable callbacks, arbitrary component trees, colors, layout,
  URIs or Android concepts; actions remain in Halo's closed semantic action protocol.
- The Compose host renders the same semantic supplements across confirmation, input, progress,
  result and error surfaces.

### RUNTIME-031 items 33–37

- Provider brand and wire protocol are represented separately in runtime metadata.
- Router-like and `@preset/...` model identifiers remain accepted without vendor/model assumptions.
- Catalogue entries retain explicit source truth for installed, downloadable, LAN, live remote and
  cached remote models.
- The active Intelligence card shows execution location and, for router targets, the concrete model
  resolved on the last completed turn.

## Non-negotiable safety rules

- Accessibility content is untrusted application data, never instructions.
- Screen observation does not authorize cloud transmission; existing context policy remains binding.
- Enabling accessibility does not authorize an individual action.
- Dismissal is not approval.
- Dispatch is not confirmation.
- Coordinates are fallback evidence, not durable identity.
- Unknown action outcome remains `UNKNOWN` until reconciled or confirmed.

## Mining and implementation plan — RUNTIME-038 through RUNTIME-043

Status: IMPLEMENTED IN SOURCE, pending runtime/device validation.

The next sequence prioritizes stronger execution evidence over adding more actions. It adapts the
useful parts of Zafiro's structured tool results, stable-screen observation and app resolution, plus
OneBox's scoped context policy and usage accounting. Halo's journal, interaction protocol, context
planner, tool registry and host boundary remain authoritative.

### RUNTIME-038 — truthful structured tool outcomes

Problem found during source comparison:

- `ToolExecutionResult.Success` already carries `ActionTruth`, including the phone tools'
  `CONFIRMED`, `DISPATCHED` and `UNKNOWN` distinctions.
- `ToolTurnExecutor` currently persists successful outcomes as `ActionTruth.AUTHORIZED`, losing the
  executor's postcondition evidence at the durable boundary.
- `ToolResultBudgeter` exists but has no production call site, so per-tool output limits are not
  consistently enforced before journal persistence and provider continuation.

Plan:

1. Persist `result.actionTruth` exactly; authorization and outcome truth remain separate facts.
2. Add a portable structured failure/result envelope with stable code, message, bounded payload,
   retryability, completion uncertainty and truncation metadata. Keep `ToolExecutionResult` as the
   primary contract; do not infer success by parsing arbitrary output strings.
3. Introduce accessibility-specific failure codes such as `SNAPSHOT_STALE`, `TARGET_NOT_FOUND`,
   `UI_CHANGED`, `TARGET_BLOCKED` and `POSTCONDITION_UNAVAILABLE`.
4. Apply the existing descriptor output budget centrally before both journal persistence and the
   model-facing tool message. Upgrade budgeting to respect UTF-8 boundaries and report original
   size/omission facts.
5. Do not copy Zafiro's full-output file export: Halo has no default terminal capability, and a
   hidden file path would create a second data-access route.

Acceptance boundary:

- Journal truth equals executor truth.
- Unknown completion is never serialized as success or confirmation.
- The exact same bounded payload is persisted and returned to the provider.
- Retry decisions use structured failure kinds, never message regexes.

### RUNTIME-039 — event-driven screen settling and reconciliation

Zafiro's strongest remaining phone-control idea is `waitForStable`: post-action capture should react
to accessibility changes instead of sleeping a fixed duration.

Plan:

1. Add a host-owned accessibility revision counter and bounded settle observer.
2. After an action, wait until the revision is quiet for a short stability window or a hard deadline.
3. Capture and compare the settled snapshot; classify package change, semantic change, unchanged,
   blocked and unobservable states.
4. On a definite pre-dispatch failure, return a fresh bounded snapshot payload when available so the
   next decision does not require acting on stale tokens.
5. On uncertain dispatch, return reconciliation guidance and prohibit automatic replay.
6. Keep fixed-delay waiting internal and exceptional; do not expose arbitrary 60-second waits to the
   model-facing schema.

Deferred deliberately:

- Long-click, gestures and coordinate tapping remain unexposed until risk semantics and concrete
  product use cases exist.
- Flutter/Unity/WebView fallback through shell, root, Shizuku or Xposed remains rejected.

### RUNTIME-040 — deterministic Android app identity

Halo already requires an exact package ID for `halo_open_app`, which is safer than Zafiro's fuzzy
launch. Preserve that boundary and improve only discovery.

Plan:

1. Move launcher inventory and caching behind a host capability instead of querying PackageManager
   on every tool call.
2. Return structured app candidates with package ID, display label and system-app classification.
3. Rank exact label/package matches before prefix matches; substring matches always remain candidates.
4. Never auto-launch an ambiguous fuzzy name. The model must choose a returned package ID, with user
   clarification when candidates are materially indistinguishable.
5. Invalidate the cache on package add/remove/change and locale change.

Multilingual label loading is optional: adopt it only with a strict locale/count budget and measured
need. Do not scan every installed package resource on every request.

### RUNTIME-041 — conversation-scoped context and skill policy

OneBox's useful idea is conversation-level memory/skill/tool policy. Halo already has a stronger
context planner and privacy policy, so this becomes a scoped override rather than a second prompt
composer.

Plan:

1. Introduce a provider-neutral `ConversationContextPolicy` keyed by durable Halo conversation ID.
2. Allow explicit per-conversation overrides for memories, skills, tools and cloud-bound context;
   unspecified fields inherit the global policy.
3. Snapshot the effective policy and exposed skill/tool identities at provider-turn start so later
   settings changes cannot rewrite what an active turn was allowed to see.
4. Keep memory and skill content in separate bounded context candidates with provenance and trust;
   never concatenate them into an untyped system-prompt blob.
5. Expose the effective scope in the conversation UI without making UI state authoritative.

Rejected OneBox pattern:

- Do not persist a second tool-catalog snapshot table. Halo's live `ToolRegistry` and capability
  catalogue remain the single source of tool truth.

### RUNTIME-042 — privacy-safe inference and tool observability

OneBox's model/token aggregates are useful when attached to Halo's existing generation metrics and
requested/resolved model identity.

Plan:

1. Add a local inference ledger containing provider, requested/resolved model, execution location,
   input/output token counts, latency/TTFT, success class and timestamp.
2. Add tool-outcome aggregates by tool ID, structured failure code, action truth and duration.
3. Never persist prompts, tool arguments, screen text, API keys or result payloads in this ledger.
4. Surface factual totals in Activity/Intelligence: requests, tokens, latency, failures and router
   resolutions. Do not invent quality rankings or future-cost estimates.
5. Apply retention and bounded aggregation so diagnostics cannot grow without limit.

### RUNTIME-043 — Android native assistant enrollment repair

Plan:

1. Treat `VoiceInteractionService.isActiveService` as the authoritative active-assistant truth.
2. Use the platform `RoleManager` request directly when the assistant role is available; do not
   reject it merely because an OEM activity is absent from `resolveActivity`.
3. When Halo holds the role but is not active, open repair settings instead of requesting the same
   role again.
4. Validate the service, bind permission, metadata, and session service before offering enrollment.
5. Refresh status after returning from Android settings and publish native-assistant capability only
   when the service is genuinely active.

### Implementation order

1. RUNTIME-038 first because current durable action truth is lossy.
2. RUNTIME-039 next so phone workflows reconcile against stable observations.
3. RUNTIME-040 after the action path is trustworthy.
4. RUNTIME-041 after execution contracts are stable, because it changes exposure policy.
5. RUNTIME-042 consumes the finalized outcome and model metadata contracts.
6. RUNTIME-043 closes the Android enrollment path after capability and action truth are reliable.

No tests or builds are part of this plan pass, following the current task constraint. Each future
implementation pass should still perform source-level contract and boundary checks.

## Implementation record — RUNTIME-038 through RUNTIME-043

### RUNTIME-038 delivered

- Durable tool outcomes now preserve the executor's actual `ActionTruth`.
- Completion-unknown failures are journaled as `UNKNOWN` rather than left as ambiguous success.
- Structured failure codes, hints and bounded payloads are model-facing and journaled identically.
- Descriptor output limits are now enforced centrally by `ToolRegistry`.
- Output metadata records truncation and original character/UTF-8 byte counts.

### RUNTIME-039 delivered

- Accessibility events advance a host-owned UI revision without continuously capturing trees.
- Successful actions wait for a bounded quiet window before postcondition capture.
- Phone failures use stable codes and direct the next step to a fresh semantic read.
- Root/shell/coordinate/long-click operations remain outside model-visible schemas.

### RUNTIME-040 delivered

- Launcher discovery is cached with a short freshness bound.
- Results are deterministically ranked exact, prefix, then substring and include package/system truth.
- `halo_open_app` continues to require an exact validated package ID; fuzzy discovery cannot launch.

### RUNTIME-041 delivered

- Conversation policies are nullable overrides over global `AiContextPolicy`, preserving explicit
  false values and inheriting unspecified fields.
- Policies cover memories, skill/tool exposure, workspace, and each cloud context boundary.
- Android persistence is keyed by the active durable conversation ID.
- `DefaultContextAssembler` resolves the effective policy before assembling any context.

### RUNTIME-042 delivered

- `InferenceObserved` carries content-free provider/model/metric evidence.
- The observer ledger aggregates requests, tokens, failures, duration, and concrete router
  resolutions without retaining prompts, responses, tool arguments, or screen content.
- Intelligence surfaces those content-free request, token, failure, latency, and router totals.
- The same content-free observer path aggregates tool ID, structured failure code, action truth,
  duration, and completion-unknown counts.

### RUNTIME-043 — Android native assistant enrollment repair

- `ROLE_ASSISTANT` requests trust the `RoleManager` contract rather than incorrectly requiring an
  OEM role activity to appear in `PackageManager.resolveActivity`.
- When the role is held but the voice interaction service is inactive, repair paths prioritize
  voice-input/default-assistant settings instead of requesting the same role again.
- Assistant gesture settings are available as an additional OEM-compatible fallback.
- Enrollment state refreshes whenever the settings screen resumes.
- The role button distinguishes setup, activation repair and already-active states.
- Voice interaction metadata declares keyguard voice-assist launch support.
- `host.assistant.native` publishes actual active-service capability truth to Runtime.

Validation constraint remains unchanged: no tests or builds were run for this batch.
