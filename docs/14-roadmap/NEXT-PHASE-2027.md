# Halo Presence + Capable Assistant

Status: **PROPOSED / PLANNED**. Audit date: 2026-09-18.

This is the next-phase plan linked from the canonical roadmap, not a replacement
implementation record or an assertion of release readiness. “2027” describes the desired
product quality and visual ambition; it does not assume future platform APIs.

## Outcome

Halo should feel like one coherent assistant: a distinctive spatial pixel-cell presence with
soft depth and light, an interface that makes its activity understandable, and dependable
actions across the phone and verified wearables. The orb, transcript, activity
history, task progress, permissions, and device state must agree.

Preserve the portable runtime, host/provider boundaries and evidence-backed hardware
integration. Strengthen local-first behavior and converge the existing execution paths. Extend the
working architecture. New agent classes or providers are not themselves outcomes.

## 1. Audit and implementation baseline

This was a source/documentation review, not a fresh build, emulator inspection,
full security audit, or hardware acceptance run. Existing test files were located
and selected implementation paths read; historical test claims were not rerun.
The pre-existing change to `AudioPerceptionEngine.kt` was left untouched.

| Area | Evidence in repository | Assessment / implication |
|---|---|---|
| Orb | `app/.../ui/glass/components/HaloStateOrb.kt` | Canvas renderer already has 11 states, pixel satellites, polygon silhouettes, glyphs, glow and touch animation. Extend it; do not replace its identity with a generic sphere. Input is a state enum; current signal motion is time-driven. |
| State projection | `app/.../ui/state/HaloPresenceProjection.kt`; `HomeViewModel.kt` | A canonical speech projection exists, but Home adds another mapping over cognitive-session state. Passive listening projects to Idle; snapshot health and task state need a consistent precedence policy. |
| App surfaces | `app/.../ui/shell/AppShell.kt`; `ui/screens/`; `ui/glass/` | Four primary tabs and secondary pages exist. Multiple component families need reconciliation. Notebook, Console, Protocol Lab and Runtime Inspector are not registered destinations. |
| Agent loop | `runtime/cognition/.../session/CognitiveSession.kt` | Streaming, repeated tool rounds, authorization hooks, result feedback and call/round limits exist. Preserve these. Add product tools and durable task semantics around this execution path. |
| Tool exposure | `app/.../di/ManualDependencyGraph.kt` | Model-facing registry is initialized with echo and current-time executors. Existing Android capabilities are not automatically model tools. |
| Authorization | `runtime/cognition/.../tools/InMemoryToolAuthorizer.kt` | Registered, validated tools are authorized. This needs permission, user-intent and side-effect checks before enabling consequential capabilities. |
| Agents | `engines/agents/.../ConversationAgent.kt` | Lifecycle methods contain placeholder comments; the module name overstates the implemented behavior. Audit other agents and consolidate around the working session path. |
| Older AI path | `engines/ai/.../AIEngine.kt` | `availableTools` is empty in both policy branches. Trace reachability and converge tool/context assembly; do not create another orchestrator. |
| Providers | `providers/openrouter/.../OpenRouterProvider.kt` | Streaming tool fragments, tool messages and model discovery exist. Contract tests and provider-specific parity remain necessary. |
| Local AI | `providers/litert/` | LiteRT-LM is the on-device path. The pinned model explicitly reports `supportsTools = false`; never advertise universal local tool support. Audit Ollama separately against its current API. |
| System assistant | `providers/android-assistant/.../AndroidAssistantProvider.kt` | Correctly modeled as external handoff, not inference. Custom prompt extras are explicitly non-standard. It does not make Halo the Android voice assistant. |
| Android actions | `host-android/.../capability/` | Adapters cover application, communication, media, navigation, web, device and memory tasks. Communication opens a dialer; dispatch success cannot establish that a call completed. |
| Reminders | `AndroidMemoryCapability.kt` | Uses exact alarms and simple duration parsing, defaulting ambiguous input to ten minutes. Needs clarification, scheduling permission handling, persistence and edit/cancel behavior. |
| Memory | `engines/memory/.../store/StoreImplementations.kt`; `runtime/cognition/.../conversation/InMemoryConversationStore.kt`; `app/.../data/AppDatabase.kt` | Several memory stores are maps; some retrieval methods return all values or have mocked-search comments. A separate Room Memory entity exists, but is not demonstrated as the cognitive-session persistence path. Reconcile both before migration. |
| Notifications | `host-android/.../system/NotificationListener.kt` | Empty class is not an operational notification listener or summarizer. |
| Android baseline | `app/build.gradle.kts`; `gradle/libs.versions.toml` | minSdk 26, targetSdk 36, compileSdk 36.1; Compose BOM 2024.09.00 amid newer build dependencies. Compatibility audit needed, not an indiscriminate version bump. |
| Hardware / release | `RELEASE_READINESS.md`; hardware matrices and test protocols | Current release acceptance is unestablished. Historical reports cite GATT 147; current severity/reproducibility needs retesting. Unit-test claims cannot satisfy hardware gates. |

### Documentation reconciliation and updated design direction

The 2026-09-18 documentation audit replaced conflicting active status/architecture/security
claims and established code plus wiring as the implementation source of truth. See
[the audit ledger](../engineering/DOCUMENTATION-AUDIT.md). Build/hardware acceptance
and source contradictions remain independent work; documentation correction does not fix code.

The user subsequently provided a [workspace mockup](../03-design/HALO-WORKSPACE-DESIGN.md).
It is the redesign reference: light/dark themes, translucent spatial-cell presence,
pastel category surfaces and five tabs including Notebook. Mockup data/devices/actions
are examples, not implementation evidence. This supersedes the earlier dark-only plan.

The subsequent [Notebook blueprint](../02-product/NOTEBOOK-WORKSPACE-BLUEPRINT.md)
and [delivery plan](../02-product/NOTEBOOK-DELIVERY.md) define persistent workspace objects,
explicit memory promotion, provenance and a visible Working Set. Their proposed module
tree and Kotlin snippets are design sketches, not the current schema or compiled APIs.

## 2. Presence v2: spatial pixel cells, subtle halo, meaningful effects

Use the user mockup's layered translucent cell cluster as the hero, with small
detached pixel markers, gentle depth, and readable state/action labels. Implement both
airy light and charcoal dark themes. Interpret halo as soft light/depth around cells.
The Notebook blueprint explicitly rejects mechanical rings and radial gauges;
put measured task progress in separate readable cards.
The state table below defines semantic behavior to apply to the cell composition.
See the design guide for screen intent, token candidates and reference acceptance.

Extend the existing renderer with a presentation model derived from runtime,
speech, session, device and provider observations. Proposed fields: phase,
input/output signal envelope, source freshness, task step, measured progress,
attention reason, connectivity/privacy badges, and animation-quality setting.
These are presentation projections; UI never starts a fake runtime transition.
Use existing telemetry first; add SDK fields only where a measured need exists.

| State / condition | Orb and halo behavior | User-facing function |
|---|---|---|
| Idle / ready | Stable spatial cells with soft settled light | Tap to start; keyboard input remains equally available |
| Passive wake listening | Small distinct marker, quiet halo | Explain that wake listening is enabled without implying active capture |
| Wake detected | One short cell-brightness pulse | Immediate tactile/visual acknowledgement |
| Capturing | Pixel bars driven by measured microphone envelope | Show actual phone/glasses source; stop/cancel accessible |
| Recognizing | Pixels align inward, indeterminate segments | Partial transcript if supported; no fake percentage |
| Reasoning / streaming | Restrained shifts in cell depth and illumination | Stream answer text; show provider and offline/cloud badge |
| Planning | Cells settle into a quiet grouping | Show known plan steps and concise intended actions in a card, not fabricated internal reasoning |
| Awaiting clarification / approval | Settled cells and attention marker | Action card with concrete choices and affected target |
| Executing | Discrete cell transitions follow observed state changes | Measured progress in task card, current action, cancel, and clear partial-completion handling |
| Speaking | Outward signal follows playback envelope when available | Stop speaking; later add validated barge-in |
| Success | Brief soft illumination, then settle | Receipt with result and undo where supported |
| Failed / recovering | Settled cells with amber attention marker | Explain failure, bounded retry and a valid recovery action |
| Stopped / mic denied | Inactive core plus explicit symbol | Clear reason and permission/settings route |
| Offline / device disconnected | Secondary badge, not automatic failure | Keep working with capabilities that remain available |

Awaiting approval, completion and connectivity are proposed presentation states
or overlays; they do not require indiscriminately expanding the runtime lifecycle.
Specify precedence: blocking authorization/failure attention, active foreground
task/speech phase, then ambient readiness; independent device/provider conditions
remain badges unless they block the current task. Never let an unrelated degraded
device hide active recording. Test simultaneous and stale observations explicitly.

Interactions: tap to begin when ready; explicit stop during capture/output; task
details through an adjacent labeled button. Long press may expose input options but
must have a visible accessible equivalent. No hidden critical gesture.

Rendering: cache geometry, bound signal sampling, avoid allocations per frame,
stop work when offscreen/backgrounded, and use power-aware quality tiers. Any
shader path is optional behind API checks with a Canvas fallback to API 26.
Reduced motion uses static state shapes and discrete transitions; 200% text,
TalkBack, 48dp controls and contrast remain gates. Audio visualization must not
create an extra microphone owner or persist audio merely to draw the orb.

## 3. Apply the language to every page

First consolidate tokens/components; then migrate routes in complete batches.
Target Home, Activity, Notebook, Devices and Settings as primary navigation.
Notebook is a new product feature; add its route with persistent working flows.
Current code still has four primary tabs. Shared elements
include compact presence, action receipts, task/approval sheets, freshness labels,
permission recovery, progress rows, and consistent empty/loading/error states.

| Surface | Planned upgrade and functional completion |
|---|---|
| Home | Orb v2, speech/text composer, streamed conversation, current task, result cards, source/provider/privacy indicators; expose stop and retry consistently |
| Notebook | NB-01–04 alpha: persistent notes, memory management, conversations, files, Inbox, lexical search and Working Set. NB-05 beta: Canvas, projects/collections, semantic search and full import/export. Expose controls only with working flows |
| Activity | Search/filter by conversation, action, device and outcome; expandable execution receipts; paging; distinguish dispatched, completed, failed and cancelled |
| Devices | Capability-driven cards, connection stages, stale telemetry, supported/limited/unverified/blocked labels and useful recovery |
| HeyCyan detail | Clear photo/video/audio modes, pending/acknowledged/completed states, media inventory/transfer progress, verified audio route; prevent duplicate commands |
| BuddyBot detail + drive HUD | Shared visual language with unmistakable stop, safety interlocks, telemetry age and connection loss; preserve edge safety authority |
| Pairing / discovery | Clear scan/select/connect/verify sequence; permission and Bluetooth-off recovery; preserve raw-only developer discovery as a separate flow |
| Settings | Prioritized sections for voice, intelligence, memory/privacy, devices, automation and diagnostics; explain settings through user outcomes |
| Intelligence | Model capability matrix, streamed/tool/vision support, local-vs-network disclosure, downloads/checks, health and explicit compatible fallback |
| Voice | Input/output route, language, wake settings, voice preview, microphone test and interrupt behavior; permission and model-install recovery |
| Permissions | Purpose, granted/denied/restricted state, affected feature and direct recovery; defer requests until relevant |
| Console | Readable structured logs, filters, copy/export with redaction, error correlation; shared tokens without hero effects |
| Runtime inspector | Real pipeline/session state, capability/provider health and timings; drill-down by request/intent identity |
| Protocol Lab / BLE discovery | Capture provenance, raw/decoded distinction, replay status, evidence confidence, transfer/audio probe state; no guessed controls |
| Shared shell / sheets / dialogs | Consistent back navigation, keyboard/insets, adaptive layout, restore after process death; compact active-task affordance where useful |
| New memory / routines / assistant setup | Follow the same components; expose these through existing navigation, adding routes only with functioning features |

For each route, verify populated, empty, loading, offline, denied, stale, failure,
retry, large-font, reduced-motion and recreation states where applicable. Audit
dialogs, drawers, snackbars and developer surfaces too. A screenshot alone does
not count as functional completion.

## 4. Assistant and agentic execution

### Target: one execution path

Converge current session/tool and engine paths on observation -> rule/context/semantic interpretation -> planning ->
capability resolution -> authorization -> host/driver execution -> outcome ->
response/timeline. LLM tool calls become typed requests into this path. They must
not call Android services or hardware directly from provider adapters.

1. Inventory existing capabilities and publish model tools only when callable,
   permitted and supported by the selected model. Bridge the two registries.
2. Use typed schemas, domain validation, target disambiguation and capability
   freshness. Treat model arguments, notifications, web pages and tool results as
   untrusted input; they cannot grant permissions or change execution policy.
3. Extend authorization with allow/deny/clarify/confirm semantics, bound to the
   task, target and arguments. Ordinary authorized reversible actions proceed;
   messages, sensitive sharing and consequential device actions get appropriate
   review. Recheck permission and device safety immediately before execution.
4. Keep current tool limits; add per-step timeout, overall deadline, cancellation,
   bounded retries and output budgets. Do not retry side effects blindly.
5. Persist task identity, step receipts and pending confirmations where needed.
   Dedupe by logical action identity. After process death, reconcile uncertain
   execution; never silently replay an external action with unknown completion.
6. Distinguish proposal, dispatch, acknowledgement and actual completion. Show an
   action log with concise reasons and results rather than private reasoning.
7. Clarify incomplete requests; support follow-ups such as “the other Alex” or
   “make that tomorrow.” Separate stop-speaking, cancel-task and stop-listening.

### Android capability ladder

Parity means a tested capability matrix for supported public APIs, permissions,
roles and integrations. It cannot promise every privileged OEM assistant feature.

| Capability group | Next deliverable | Completion boundary |
|---|---|---|
| Time, torch, volume, app launch | Wire existing adapters to typed tools and deterministic intents | Actual state where observable; app launch is dispatch only |
| Search, browser, maps, media search | Validate targets and installed handlers; return useful action receipts | External UI handoff is not confirmed downstream completion |
| Timers, alarms, reminders | Reliable parsing, clarification, persistent IDs, list/edit/cancel, timezone/DST/reboot handling | Check exact-alarm access and provide truthful fallback |
| Calls and messages | Contact disambiguation, dialer and compose flows; confirmed send only through supported integrations | Never report a sent message merely because composer opened |
| Calendar | Read/create/update with explicit scope and permission, or system insertion UI | Confirm final event identity only when observed |
| Media playback | Session-aware play/pause/skip and route handling where authorized | Report unavailable session or permission accurately |
| Notifications | Real NotificationListenerService, per-app opt-in, filtering, summaries, dismissal/reply where supported | Protect lockscreen-sensitive content; untrusted text cannot invoke tools |
| Memory | Durable remember/search/edit/forget/export; provenance and per-provider sharing controls | Verify deletion across storage, retrieval caches and outbound context |
| Wearable vision | Verified photo -> transfer -> selected vision provider -> useful answer | Consent and real media receipt required; no speculative live stream |
| Routines | Explicit time/device/context triggers, scoped actions, run history, disable and cancellation | Durable scheduler; no unbounded autonomous background loops |
| AppFunctions | Bounded compatibility/permission spike, then supported integrations | Feature and caller authorization must be established on target devices |

### Make Halo an Android assistant

Implement host-owned `VoiceInteractionService`, session service/session and their
manifest/metadata, role onboarding, assist invocation and role-revocation recovery.
Avoid resolving Halo itself as an external assistant and producing handoff loops.
Retain in-app and wearable-button entry points for users who keep another default.
Screen context is optional, explicitly enabled and constrained by platform access.

Separate assistant-role lifecycle from ordinary foreground microphone capture.
Validate screen-off operation, microphone permission revocation, foreground-service
start restrictions, route switching, audio focus, interruptions and process death.
Do not promise universal low-power hardware hotword support. Start with push-to-talk
and verified wearable buttons, then qualify wake-word support per device.

## 5. Current SDK/API alignment

Official sources checked 2026-09-18. Recheck at implementation time and record exact
dependency/API revisions in the PR. Do not replace the Kotlin runtime with a cloud
agent SDK simply to obtain tool calling that already exists.

| Source | Required work |
|---|---|
| [Android VoiceInteractionService](https://developer.android.com/reference/android/service/voice/VoiceInteractionService) | Separate lightweight assistant service from active session work; implement selection and lifecycle correctly |
| [Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types) | Microphone service declarations and while-in-use restrictions; test valid entry points and denied states |
| [Android 17 target changes](https://developer.android.com/about/versions/17/behavior-changes-17) | Compatibility branch for API 37; evaluate background, large-screen and permission changes before target migration |
| [Local network permission](https://developer.android.com/privacy-and-security/local-network-permission) | Current target is 36: do not add/request ACCESS_LOCAL_NETWORK prematurely. At target 37, handle applicable LAN access and denial for Ollama/device networking; verify each transport |
| [AppFunctions overview](https://developer.android.com/ai/appfunctions) | Android 16+ feature availability and EXECUTE_APP_FUNCTIONS authorization are explicit gates; do not assume any third-party assistant can call all apps |
| [Compose BOM mapping](https://developer.android.com/develop/ui/compose/bom/bom-mapping) | Choose a tested stable BOM/build-tool set, update in isolation and verify baseline before visual migration |
| [OpenRouter tool calling](https://openrouter.ai/docs/guides/features/tool-calling) | Preserve assistant tool calls, matching result IDs, schemas and subsequent rounds; test incomplete streamed arguments, multiple calls, unsupported models and cancellation |
| [Ollama tool calling](https://docs.ollama.com/capabilities/tool-calling) | Implement/test provider-specific message and streaming semantics for supported models; do not copy the OpenRouter wire format blindly |

For LiteRT-LM and Sherpa, inventory the pinned native artifact/version, ABI coverage,
model licensing/checksums and lifecycle contract before scheduling an update.
Their latest upstream revisions were not verified in this review. Provider
acceptance must include text-only, supported tools/vision, timeout, model unload,
memory pressure and cancellation. Unsupported modalities remain explicitly off.

## 6. Sequenced delivery batches

The UI order follows the requested orb-first direction. Runtime work supplies real
telemetry and actions incrementally. Dependencies below are engineering workstreams,
not a request to launch multiple agents or rewrite the kernel.

Implementation must also respect the repository's hardware evidence sequence and
frozen runtime/SDK boundaries in `.agents/AGENTS.md`. This proposed roadmap does not
lift that freeze. UI and documentation work can proceed within existing contracts;
contract-changing batches require the stated evidence gate and focused RFC first.

| Batch | Scope / ownership boundary | Depends on | Exit evidence |
|---|---|---|---|
| NP-00 | Baseline, route/capability inventory, docs reconciliation, compatibility matrix | None | Reproducible build/test baseline; dated implemented/tested/hardware-verified ledger; proposed contract changes identified |
| NP-01 | Unified presence projection + Orb v2 in app UI; minimal telemetry adapters | NP-00 | Every state and overlap scenario demonstrated with deterministic fixtures; accessible interactions; no false progress |
| NP-02 | Shared tokens/components, Home and active-task/result surfaces | NP-01 | Voice/text/cancel/retry flow works; no state disagreement; representative phone frame measurements |
| NP-03 | Activity, Notebook, Devices, HeyCyan, BuddyBot, pairing | NP-02; NB-01 for Notebook route | Complete state coverage, preserved safety/discovery boundaries, no dead controls; Notebook alpha tracked separately through NB-04 |
| NP-04 | Settings, Intelligence, Voice, Permissions, developer surfaces and overlays | NP-03 | Route checklist complete, 200% text/TalkBack/reduced-motion coverage, consistent visual QA |
| NP-05 | Tool-capability bridge, policy, timeouts, action receipts and basic phone tools | NP-00; integrate NP-02 surfaces | At least 10 specified commands complete through the same pipeline; validation/denial/cancellation/duplicate-call negatives pass |
| NP-06 | Provider contract alignment and local model/tool capability reporting | NP-05 | Contract fixtures for OpenRouter/Ollama; honest native capabilities; compatible explicit fallback without replaying side effects |
| NP-07 | Default-assistant role, voice session, background/audio lifecycle | NP-05/06 | Role accept/deny/revoke, assist entry, screen lock, audio interruption and process-death checks on target phones |
| NP-08 | Reminders, notifications and bounded routines; integrate Notebook context and durable receipts | NP-05/06; NB-02/03 for workspace integration; NP-07 for background paths | Edit/cancel works; triggers produce auditable single execution; source/context links survive restart |
| NP-09 | Full wearable loop, performance, release hardening | NP-01–08 | Physical acceptance, burn-in and 7-day test evidence; no open P0/P1 blockers |

Notebook is an explicit companion workstream: NB-01 starts after NP-00 and shares
NP-02 components. NB-02 follows NB-01 and integrates NP-05/06 for contextual answers;
NB-03 builds on the same persistence contracts; NB-04 requires verified media events.
Full workspace alpha requires NB-01–04, including offline editing, process-death
restoration and the note -> memory -> Working Set -> Home answer -> Activity -> source loop.
NB-05 is a later beta milestone, not required to advertise the narrower alpha honestly.

Each batch should be split into focused PRs with one reviewable user flow. Do not
estimate this as one “integration week.” Calendar estimates require NP-00 results,
available hardware and team capacity. First useful release slice: NP-00/01/02 plus
NP-05 basic tools, followed by all remaining pages before the feature-rich release.

Substantial SDK/authorization/assistant contract changes should follow the existing
[RFC process](../rfc/0000-rfc-process.md). Produce focused RFCs before implementation;
this roadmap does not claim those contracts are already approved or implemented.

## 7. Remaining requirements traced to documentation

Boxes are checked only for completed, scoped work. Some requirements have partial
implementations; unchecked means acceptance remains to be established.

- [x] **DOC-01 / NP-00 (documentation only):** Reconciled active authority/status
  pages and missing guardrail references; replaced conflicting completion narratives.
  See the audit ledger. Current build/hardware baseline and code fixes remain open.
- [ ] **UI-01 / NP-01–04:** Complete every route/state, accessibility and performance
  gate. Sources: Halo Glass design, UI architecture and UI implementation status.
- [ ] **COG-01 / NP-05–07:** Observation through capability execution and spoken
  outcome uses one runtime path. Source: CONSTITUTION sections 7–16 and Alpha-W1.
- [ ] **COG-02 / NP-05:** Preserve intent identity and execution semantics; safety
  recheck immediately before physical action. Source: intent-execution contract.
- [ ] **AI-01 / NP-06:** Correct provider modalities, context policy, tool reporting
  and separation of handoff from inference. Source: AI provider architecture.
- [ ] **NB-01–04 / workspace alpha:** Persistent notes/conversations/files, Inbox,
  lexical search, explicit Working Set and provenance; local editing needs no AI provider.
  Source: Notebook blueprint and delivery plan. Canvas/projects/hybrid search are NB-05 beta.
- [ ] **MEM-01 / NB-02 + NP-08:** Searchable/editable/deletable local memory and “remember”
  flow; verify forgetting across context, indexes and caches. Workspace storage does not
  imply automatic memory promotion. Sources: Notebook delivery and FEATURES.
- [ ] **AUT-01 / NP-08:** Persisted IF/THEN time/device/context workflows, disabled
  state, history and bounded execution. Source: FEATURES and engineering WORKFLOW.
- [ ] **NOT-01 / NP-08:** Permissioned notification intake, summarization and
  wearable delivery with filtering. Source: FEATURES notification summarizer.
- [ ] **SEC-01 / NP-05–09:** Verify outbound context gate, secrets/backup protection,
  redacted diagnostics, plugin boundaries and adversarial tool tests. Source:
  THREAT_MODEL. Treat proposed controls as requirements until demonstrated;
  prompt sanitization alone does not establish injection resistance.
- [ ] **HW-01 / NP-09:** Reconcile capture/vendor evidence with capability claims;
  preserve unknown traffic and provenance. Sources: HeyCyan implementation status,
  hardware confirmation matrix, protocol docs and constitutional evidence rules.
- [ ] **HW-02 / NP-09:** Photo/count/media receipt, video start/stop, audio recording,
  Wi-Fi/media transfer, voice-button route, battery freshness and reconnect tested
  on physical glasses. Source: hardware confirmation matrix and test protocols.
- [ ] **HW-03 / NP-07/09:** Real PCM -> STT -> observation -> reasoning/planning ->
  action -> TTS -> wearable output, including fallback microphone. Sources:
  CONSTITUTION Alpha-W1 and roadmap PR-020–022.
- [ ] **PERF-01 / NP-09:** Measure wake latency, local answer time, background power
  and offline completion. PRD targets: <150ms wake latency, <1s local answer,
  <1.5% battery/hour background, >80% offline system-control tasks. Define timing
  boundaries and command set first; these are targets, not measured achievements.
- [ ] **REL-01 / NP-09:** Verify historical GATT 147 issue, run 8–12h burn-in and the
  seven-day physical protocol; publish actual Android release readiness with build,
  commit, phone/OS, firmware and evidence links. Sources: CURRENT-STATE,
  RELEASE_READINESS and docs/testing.

## 8. Acceptance and definition of done

The initial command suite: current time, torch on/off, volume adjustment, app
launch, web search, navigation launch, dial a disambiguated contact, timed reminder,
remember a fact, and retrieve/delete that fact. Classify actions by actual outcome;
do not score an external handoff as confirmed execution. Add notification summary,
media control, routines and verified wearable capture in their later batches.

For each command include happy path, ambiguous input, permission denied/revoked,
unsupported target, provider loss, cancellation and duplicate delivery where
relevant. Multi-step scenario: resolve a contact, prepare a message, present its
recipient/content, and open the composer; interruption must not falsely report
that it was sent. Hardware scenario: capture a photo, await verified media receipt,
describe it with the chosen permitted provider, and deliver the answer.

Verification layers: deterministic projection/tool/session tests; provider wire
fixtures; Compose screenshots and interaction/accessibility tests; simulator
end-to-end runs; instrumented Android role/audio/permission tests; real phones and
glasses. Attach evidence rather than broad “100% complete” scores.

Proposed UI budget: responsive interaction within 100ms, target 60fps on a declared
mid-range reference phone, no continuous orb animation work offscreen, and no
material regression against measured idle power. Record frame-time distributions,
memory, thermal and battery measurements before making quality claims. Preserve
functional fallbacks for minimum API and low-power devices.

Phase complete means every route has been reviewed, the assistant capability
matrix is tested, all required checklist items have evidence or an explicit scoped
deferral, and release-blocking defects are resolved. Experimental capabilities
remain labeled and excluded from advertised completion.
