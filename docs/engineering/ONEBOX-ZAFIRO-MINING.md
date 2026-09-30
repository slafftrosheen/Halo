# OneBox and Zafiro pattern mining

Next product focus (2026-09-28): [Notebook and Study mining](NOTEBOOK-STUDY-MINING.md), with
[RFC 0002](../rfc/0002-notebook-editor-and-study.md) as the plan-first delivery sequence.
This supersedes the runtime-only next-pass priority below, without completing or deleting its backlog.

Status: ACTIVE IMPLEMENTATION NOTES. Updated 2026-09-28.

These archives are design references, not dependencies:

- `OneBox-main (1).zip`
- `zafiro-main.zip`

Halo keeps its own Runtime journal, interaction surfaces, tool policy, capability catalogue,
provider contracts and Android host boundary. Reference code must not create a parallel agent
runtime or move Android APIs into Runtime modules.

## Decision matrix

| Reference pattern | Halo decision | Halo owner |
|---|---|---|
| Zafiro bounded accessibility tree, semantic roles and opaque node indices | Adapt with snapshot-scoped opaque tokens, redaction, explicit truncation and a compacted projection (resolved roles, collapsed inert containers, off-screen summaries, third-based regions) | `host-android` |
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

## Mining and implementation record — RUNTIME-044 through RUNTIME-046

Status: IMPLEMENTED IN SOURCE. No build or test run in this pass; verification was source-level review
of each contract boundary, plus one correction of this document's own history (see the discrepancy
note below).

### Document correction

The implementation record for RUNTIME-038 through RUNTIME-043 above was verified directly against
source in this pass and is accurate. `docs/engineering/REFERENCE-HARVEST.md` had been reporting the
same work as OPEN or PARTIAL; those rows were stale and have been corrected. This document is not
evidence that its own plan was implemented, and neither is the harvest ledger.

### RUNTIME-044 — provider failure truth

Problem found during source comparison:

- `AiErrorType.CONTEXT_TOO_LARGE` had consumers (`AiErrorPresenter`, `AiProviderRouter` mapping it to
  `TargetRejectionReason.MEMORY_PREFLIGHT_FAILED`) but no producer anywhere in `providers/`.
- Every OpenAI-compatible context-overflow response is an ordinary `400`, so it was reported as
  `INVALID_REQUEST` / `PERMANENT` and became indistinguishable from a malformed request.
- `DefaultRetryPolicy.nextDelay` grew without bound and applied `1 shl (attempt - 1)` directly;
  the shift overflows into a negative `Int` at `attempt >= 32`, and a long chain would have waited
  hours per attempt.

Implemented:

1. New `sdk/provider-sdk` owner `ContextOverflow` decides, once, whether a provider code or message
   means "request exceeded the model's context window": an authoritative machine-code set first, then
   a deliberately narrow prose phrase set so unrelated `400`s cannot be mislabelled. Providers map
   their own wire response through it instead of each inventing string checks.
2. `OpenRouterProvider` maps a `400` recognized by that owner to `AiErrorType.CONTEXT_TOO_LARGE` and
   keeps `Recoverability.PERMANENT`: the failure is actionable (compact context or choose a
   larger-context model) but replaying the identical request is still wrong.
3. `DefaultRetryPolicy.nextDelay` bounds the exponent and caps a single wait at 30 s. The existing
   100/200/400/800/1600 ms sequence is unchanged, so the recorded backoff contract still holds.
4. `OpenRouterFailureMatrixTest` gains three cases: classification from a provider code,
   classification from prose when no code is sent, and a negative case proving an unrelated `400`
   that merely mentions tokens stays `INVALID_REQUEST`.

Acceptance boundary:

- A context overflow is never reported as a malformed request, and never as retryable.
- The classifier is the only place that decides this, and it is provider-neutral.
- No backoff delay is ever negative or unbounded.

Deliberately rejected from the same reference: multiplicative retry jitter. Halo is a single-client
on-device runtime with no retry herd to spread, and provider-supplied `Retry-After` is already
honoured upstream.

Remaining and explicitly not done: `DefaultRetryPolicy.maxAttempts` still classifies by
substring-matching human-readable reason text, which contradicts RUNTIME-038's rule that retry
decisions use structured failure kinds. That needs a typed kind on `Outcome.RetryableFailure` plus
migration of the producers, so it is recorded as RUNTIME-044b rather than half-done here.

### RUNTIME-045 — built-in skill declaration ownership

Problem found during source comparison — a real shipping defect, not a style issue:

- Ten of eleven built-in skills are declared twice: in `DefaultSkillRegistry.defaultBuiltInSkills()`
  and as a shipped `assets/skills/*.json` manifest.
- `BuiltInSkillManifestLoader` merged them with `(defaults + manifests).associateBy { it.id }`, which
  is last-wins, so the asset always won.
- For `halo.phone` the asset declared `EXTERNAL_SIDE_EFFECT` while the registry declared `SENSITIVE`.
  The weaker classification of Halo's highest-risk capability is what actually shipped.
- `halo.reminders` showed the inverse (asset stricter than registry), which rules out simply
  preferring either document.
- Four `assets/skills/*.yaml` files were unread (the loader filters `.json`) and duplicated four JSON
  manifests in a different casing, misrepresenting the authoritative schema.

Implemented:

1. New `BuiltInSkillMerge` owner resolves duplicate ids by keeping the strictest declared risk at
   both skill and per-tool level (`SkillRisk` declares its constants in ascending severity, so
   ordinal order is severity order) and unions tools and required permissions. Neither document can
   weaken the other or silently drop a requirement, and a registry-declared approval default is
   preserved for tools the manifest also declares.
2. `halo.phone.json` now declares `SENSITIVE`, agreeing with the registry instead of understating it.
3. The four unread `*.yaml` assets were deleted.

Acceptance boundary:

- No manifest can ship a weaker effective risk than the registry declares for the same skill id.
- `halo.phone` is `SENSITIVE` through both declarations.
- Every built-in skill has exactly one authoritative document, and unread duplicates do not exist.

### RUNTIME-046 — compacted phone observation (delivered in source)

Halo was ahead of the reference on consent, redaction, opaque snapshot-scoped tokens, receipts and
settled snapshots, but the projection itself was thin: `AccessibilitySnapshotFormatter` was a loose
substring chain over class names that never consulted the parent node, it emitted raw bounds for
every node, and it neither collapsed containers nor said anything about what a scrollable had
hidden. All five planned items are now in source.

1. **Roles are resolved, not guessed.** `AccessibilityProjectionRules.classify` is a pure object
   with no Android dependency: platform editability first, then an exact class table, then a
   *bounded* suffix table ordered specific-first (`ToggleButton` before `Button`,
   `AutoCompleteTextView` before `TextView`), then scroll/check/click flags, and finally the parent.
   A `LIST` parent makes an unclassified child a `LIST_ITEM` instead of an anonymous container, and
   `TAB`, `CHIP`, `TOOLBAR` and `DIALOG` are now expressible roles.
2. **Inert containers collapse.** A non-interactive node with no text, description or view id that
   is neither selected nor focused emits nothing (counted as `collapsed_container`) while its
   children are still emitted, so a wrapper costs no node and no line.
3. **A scrollable reports what it hid.** Pruned off-screen rows are counted and previewed (bounded
   to three redacted previews) on the scrollable node itself. The pruner is *transparent*: an
   off-screen unlabeled wrapper promotes the labelled row inside it rather than consuming the item,
   which is the common recycler case.
4. **Position is a third.** `region=top-left` replaced `bounds=[...]` in model-visible text. Bounds
   stay on the contract as evidence and as the fallback when the display size is unknown; no action
   path ever used coordinates.
5. **An unreadable screen says so.** `ScreenContextSnapshot.inspectability` distinguishes a
   projection with content from an empty or root-only tree and from a surface exposing nothing
   labelled or interactive, and `halo_read_screen` answers `status=unreadable_surface
   next=REPORT_TO_USER` with an explicit do-not-retry advisory instead of presenting a blank screen.

One item beyond the plan, because the same comparison exposed it: failure reporting. A nullable
snapshot collapsed "nothing to observe" and "Halo refuses to observe this app" into a single string,
which invites a model to retry the one thing it must never retry. Sealed `ScreenCaptureOutcome` now
separates `Captured`, `NoActiveWindow` (transient) and `PackageBlocked` (permanent, carrying
`AccessibilitySafetyPolicy.blockReason`), and a disabled service reports `ACCESSIBILITY_DISABLED`.

Token stability was decided deliberately, not inherited: the digest covers the node, its true tree
path and its *intrinsic* role only. Parent-derived roles such as `LIST_ITEM` are display information,
so a token cannot change merely because a list gained or lost a wrapper.

Unchanged by this work: node/depth/character budgets, redaction, untrusted-content framing, opaque
snapshot-scoped tokens, settled snapshots, receipts, and the per-action consent model. The only
contract edits are additive fields with defaults on the existing screen types.

Acceptance boundary:

- No class name is classified by substring containment; every role comes from an exact match, a
  whole-name suffix or a platform flag.
- A row inside a list is a `LIST_ITEM`, and a labelled control inside a list keeps its own role.
- A model never receives one line per inert wrapper.
- A scrollable that pruned rows tells the model how many and previews up to three of them.
- No model-visible text contains raw pixel coordinates.
- A screen with nothing readable is reported as such and never as an empty screen.
- "Try again", "never on this app" and "there is nothing to read" are three different answers.

Not verified: no build and no test was run for this pass, and the JVM tests added for the new pure
rules object have never been executed.

### RUNTIME-047 — local stop sequences (planned, not started)

OneBox's runtime post-processes output because the native API has no stop parameter: accumulate the
stream, find the first occurrence of any stop string, emit only the not-yet-emitted prefix, cancel the
native process, and report normal completion. Halo's generation contract has no stop concept, so this
needs a bounded `stopSequences` field on the local generation configuration plus the same streaming
truncation, with cancellation still winning over a stop hit.

### Implementation order for the next pass

1. RUNTIME-047 first: it is the last open LiteRT row and is contained to the local provider.
2. RUNTIME-045b next: it changes the skill document contract across modules.
3. RUNTIME-044b last: it touches every producer of `Outcome.RetryableFailure`.

RUNTIME-046 was first in the previous order and is now delivered; before building on it, run the
knowledge it depends on rather than trusting this record. A build (and ideally the new
`AccessibilityProjectionRulesTest`) is the cheapest way to retire that debt, and the physical-phone
checklist in `docs/13-engineering/PHONE-ACCEPTANCE.md` is what turns the projection into a verified
answer rather than a plausible one.

No tests or builds are part of this pass either, following the current task constraint.

## Mining and implementation record — RUNTIME-056

Status: **BUILD VERIFIED / UNIT VERIFIED / PHYSICAL DEVICE NOT RUN.** Reference inspected: the
OneBox agent tool catalogue `feature/ai/src/main/java/com/shifenmiao/ai/agent/tool/**` and its
network services (`WebFetchService`, `NetworkInfoService`, `UrlCheckService`), which is where the
archive is ahead of Halo: it can *describe* and *read* through the assistant, not only act.

### Adopted ideas (fitted to Halo's own contracts)

| OneBox tool | Idea | Halo outcome |
| --- | --- | --- |
| `get_device_info` | The assistant must be able to describe the device it runs on | `halo_device_info` reports model, Android version, screen, memory, locale. OneBox reads JVM heap only; Halo reports total RAM and screen refresh from platform sources |
| (network status) | Connection truth as data | `halo_network_status` reads the active network's reported capabilities: transports, validated, metered, roaming, link speeds, interface and local addresses. SSID only when location access is granted |
| (battery, absent in OneBox) | Battery should be a first-class answer | `halo_battery_status` reads the sticky `ACTION_BATTERY_CHANGED` broadcast so the answer matches what Android publishes, including `not_charging` vs `discharging` |
| `checksum_tool` | Verify integrity of user data | `halo_checksum` over text or a workspace-imported file. OneBox accepts a `uri`; Halo deliberately accepts only a workspace object id, because arbitrary-URI hashing would create a second file-access route |
| `fetch_webpage` | Read a page, do not just open it | `halo_fetch_url`, bounded far more tightly than the reference: manual redirect following (the reference relies on `followRedirects` and cannot see a redirect into a private address), private/loopback/link-local refusal by literal *and* resolved address, 512 KB cap, text media types only, no credentials |
| `theme_setting`, `night_mode_setting`, `font_scale_setting`, `start_entry_setting` | Deep-link settings as a capability | `halo_open_settings` becomes device truth: only sections an installed activity answers are offered, package-scoped screens are addressed with their package. Halo keeps its own settings surface instead of one tool per vendor screen |
| clipboard-shaped file tools | Move text between apps | `halo_clipboard_read` / `halo_clipboard_write` with untrusted-content framing and the focused-app limitation reported as its own outcome |
| `read_text_aloud` / `stop_reading` | Speak text | Not ported: Halo already owns speech output through the assistant and TTS path, and a second reading channel would compete with it |
| `manage_todo`, `create_note`, `memory_get/write` | Durable user data | Not ported: Halo's Notebook and Memory surfaces already own these, and the reference's second store would duplicate them |

### Rejected deliberately

- **`vision-automation`-style screenshot + act tools** (`VisualAutomation`, `act_on_ui`, `automate_ui_task`).
  Halo's accessibility path already reads semantic snapshots with opaque tokens, redaction, postaction
  receipts and per-action consent. A parallel coordinate/screenshot actor would be a second, weaker
  consent model.
- **`send_http_request`, `download_file`, `search_web`, HTTP-registered tools, cloud/WebDAV, PDF,
  image pipeline** (watermark, compress, convert, EXIF, WebP, base64). Each needs a dependency,
  a credential, a storage route or a UI surface Halo does not have yet. Nothing here is needed to
  make the phone agent *useful*; they stay in the ledger as future work, not as half-wired tools.
- **OCR / barcode / calendar / SMS / call-log tools.** They need permissions Halo does not declare
  (`HostPermission` has no reading of calendar or messages), and adding a permission surface is a
  product decision, not a mining outcome. Recorded as the next permission-shaped milestone.

### Defects this pass found in Halo (not in the reference)

1. **Host descriptor dispatch was effectively one category.** Every `ExecutorKind` fell through to
   a default descriptor, and `ToolRegistry`'s first-wins catalogue kept the first kind registered
   for each tool name. All non-navigation host tools were therefore catalogued as `NAVIGATION` +
   `EXTERNAL_SIDE_EFFECT` + `NEVER_REPLAY`, and `ConfirmationPolicy.ALWAYS` declarations
   (`halo_ui_set_text`, `halo_ui_click`, `halo_ui_scroll`, `halo_ui_global_action`) were downgraded to
   `USER_POLICY`, which a user `ALWAYS_ALLOW` policy silently overrides. Kinds now claim an exact
   definition or return nothing; unclaimed definitions take the registry's conservative fallback.
2. **A retired provider id could survive forever.** `AiSelectionRepository` set the legacy migration
   marker on every start, so a stored `provider.llamacpp.native` restored after that marker was set
   was never repaired. Retired ids are now repaired on every start; the marker still guards the
   one-time legacy model-key lookup. `LiteRtSelectionTest` (previously failing) and two new engine
   tests pin this.

### Verification

`:engines:ai:test`, `:runtime:cognition:test` and `:app:testDebugUnitTest` pass, including
`NativeAbilityToolsTest` (device/battery/network honesty, clipboard round-trip and untrusted
framing, published checksum vectors, web-reader refusals, ringer/brightness/timer/settings failure
shapes) and `HostToolDescriptorDispatchTest` (exact claiming, no fallback classifications, capability
requirements per ability, consent classification). Both round-trip through `BuiltInSkillManifestTest`,
which requires every built-in skill to ship a validated manifest. No physical-phone run was performed.
