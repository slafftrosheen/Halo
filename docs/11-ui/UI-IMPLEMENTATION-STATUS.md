# UI source inventory

## FEATURE-065 / MCP-UI-001 — integrations control plane — 2026-10-07

Settings → Integrations and Skills → MCP counts open the MCP server list and
server detail routes. Add/edit, enable/disable, explicit Connect/Refresh tools,
per-tool toggles, credential replacement/removal and integration removal reuse
the existing manager, Room tables, SecretStore and single ToolRegistry overlay.
New servers start disabled. Opening or observing configuration performs no MCP
network request. Current connection state comes from live manager state, never
a stored last-connect timestamp. Cached tool metadata remains inspectable.

Server configuration is not execution authority; trust is not permission;
connection is not tool success; tool enabled is not automatically approved.
Bearer credentials never enter Room, settings or Brain export. Settings never
invokes remote tools. Descriptions remain untrusted data; risk is not editable.
Explicit discovery cancellation reaches the existing client/transport and
registry synchronization removes disabled, replaced or removed live surfaces.

See [implementation and validation](../engineering/MCP-UI-001-INTEGRATIONS.md).


## FEATURE-064 / INBOX-001 — actionable private notifications — 2026-10-07

Home and Notification Settings open the secondary Notifications workspace and
notification detail routes. Existing listener ingestion, 24-hour Room history,
and per-app policies remain the authorities. Only ALLOW rows are individually
browsable; SUMMARIZE_ONLY contributes to explicit deterministic digests only.
Search and app filters operate on permitted projections. Live registry actions
expire independently of history; disconnected or restored history cannot imply
replyability. Reply and dismiss require explicit confirmations. Reply success
means DISPATCHED (“Reply handed to app · Delivery not confirmed”); dismissal is
only requested until listener reconciliation. There is no inference, cloud
summary, automatic notification memory or automatic tool execution.

See [INBOX-001](../engineering/INBOX-001-ACTIONABLE-NOTIFICATIONS.md) for boundaries, validation and manual acceptance.


## LOCAL-AI-RECOVERY-001 — bounded local thinking policy — 2026-10-07

Qwen3 CHAT, FAST and AGENTIC now explicitly disable thinking through LiteRT-LM
0.17.1 `ThinkingConfig`; exact thinking-capable REASONING targets receive a
64-token budget. Capability metadata does not enable thinking by itself.
Provider-boundary `ToolUseMode.NONE` removes both native tools and Halo prompt
protocol instructions. Diagnostics use the same explicit policy without updating
production health. Role probe suite v2 and performance compatibility epoch 2
retain older history but exclude it from current recommendations/calibration.
The 90-second interactive deadline is unchanged; its local timeout diagnostic
contains execution metadata only. Validation and the single phone acceptance
are recorded in `docs/engineering/LOCAL-AI-RECOVERY-001.md`.

Automated gate: 721 tests, zero failures/errors/skips; debug APK assembled.
Physical acceptance is pending the user's manual one-pass run; no new phone
performance or conversational-quality claim is made.


## RUNTIME-063 — local role qualification — 2026-10-06

Explicit On-device developer actions now qualify installed exact artifacts by
FAST, CHAT, REASONING, AGENTIC and VISION separately. Cold primer is excluded from
three warm FAST samples; deterministic CHAT/reasoning probes and the existing
exact-target tool continuation produce process/session evidence. Physical identity
is verified before/after; backend drift invalidates evidence. Matrix execution is
serial, cancellable, failure-isolated, and cannot preempt active user inference.
Diagnostic traffic bypasses production EWMA, role/CHAT settings remain user-owned,
and the catalog/download/GPU gates remain unchanged. Developer tests are collapsed;
System Check reads recent evidence without running probes. Copy exports bounded
metadata only. No database migration or automatic configuration.

Full serial automated gate passed: 691 tests, zero failures/errors/skips, debug APK assembled.
Both-phone physical qualification remains pending;
no default-model recommendation or following batch selected.
See [qualification contract and acceptance plan](../engineering/LOCAL-MODEL-ROLE-001.md).


## STABILIZATION-PROVIDER-001 - 2026-09-30

**IMPLEMENTED / UNVALIDATED.** Intelligence renders bounded summaries and no model catalogue. Provider detail is the credential entry surface. Voice root has input/output/routing summaries; recognition and output have dedicated routes. Model rows are compact and lazy. Home and System Check use readable status/proof and provider-specific navigation.

See [source map and deferred acceptance](../engineering/PROVIDER-CONVERSATION-STABILIZATION.md). Build, tests and device acceptance were not run. Earlier dated sections below are historical.


## P8 provider UX and conversation reset — 2026-09-30

General conversational answering is a core Halo capability and does not depend on tool availability. DirectResponse/CHAT is separate from Agentic actions; configuration, authentication, model discovery and generation proof are separate. Speech uses the selected PCM provider, protected ElevenLabs opt-in, phone-only settings tests and host-owned route policy. See [implementation and deferred acceptance](../engineering/PROVIDER-UX-AND-CONVERSATION-RESET.md). Earlier sections retain historical context.


## PRODUCT-P0-001 — source update, 2026-09-30

Earlier inline Intelligence catalogue/provider forms are superseded by the short hub, Provider Center, Provider Detail, lazy Model Browser and role/routing page. Local development controls moved to OnDeviceModelsScreen; context/advanced controls remain on a separate page. Voice adds output selection, focused ElevenLabs setup and a language picker. No rendered or device acceptance was performed.

See the [control-plane source map](../engineering/PROVIDER-CONTROL-PLANE.md) for ownership, official contracts, bounds, security and the later QA plan. Build/tests/device acceptance: not run. Earlier dated descriptions below remain historical.

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.
Route/source presence is not interaction, visual or accessibility acceptance.

[AppShell](../../app/src/main/java/com/halo/app/ui/shell/AppShell.kt) registers:

| Route | Surface |
|---|---|
| home | HomeScreen |
| activity | ActivityScreen |
| notebook | NotebookScreen |
| notebook/note/{noteId} | NoteEditorScreen |
| devices | DevicesScreen |
| device/{deviceId} | DeviceKind dispatch to HeyCyanControlScreen/BuddyBotControlScreen |
| settings | SettingsScreen |
| ai_settings | IntelligenceScreen |
| voice_settings | VoiceSettingsScreen |
| permissions | PermissionsScreen |
| discovery | PairingScreen |
| runtime_status | RuntimeScreen |

Notebook now has a persistent NB-01 notes route with quick creation, local lexical search,
Inbox/archive/trash views and an autosaving plain-text/Markdown editor with recovery drafts.
Its hierarchy, pastel action signals, compact item rows, editor toolbar and status treatment use
the root `HaloDesignMockup.png` as visual direction without exposing its unsupported controls.
Memory, Working Set,
files, conversations and Canvas remain proposed in the
[Notebook plan](../02-product/NOTEBOOK-DELIVERY.md); the initial route does not imply those flows.

Console, Protocol Lab and Runtime Inspector are not registered destinations.
HeyCyanDiscoveryScreen exists separately; the discovery route above uses PairingScreen.
A probe/diagnostic class, label or old screen is not a reachable page.

Halo Presence uses the canonical `HaloSignalFace` renderer (outer semantic state halo + inner graphite OLED pixel display). Activity, Notebook, Settings, Devices, and primary navigation reuse the unified grid-based `HaloGlyph` pixel pictogram system (`HaloIcon`).

System Check is reachable at `runtime/system-check` (via Settings -> Runtime Status -> System Check) providing diagnostic hardware, local model, microphone, audio route, and tool approval probes.

Acceptance still needs action/back behavior, empty/loading/offline/denied/error/stale states,
large text, TalkBack, reduced motion, recreation, frame timing and physical workflow checks.


## 2026-09-18 workspace correction

The latest main-line correction restores Home keyboard submission, streaming/completed response
presentation, voice start/stop/interruption and session cancellation under the new workspace
visual language. Notebook exposes only implemented NB-01 controls: create note, lexical search,
Inbox/archive/trash filters and persisted note editing. Devices no longer shows sample saved
hardware or dead quick actions. Settings exposes routed configuration pages plus a working persisted System/Light/Dark appearance selector.

Light and dark color schemes are implemented through HaloTheme. AppearancePreferences persists
System/Light/Dark selection and MainActivity applies it at the app root; System remains the default.


## Secondary-surface migration

Intelligence, Voice, Permissions, Pairing, HeyCyanControlScreen and BuddyBotControlScreen now use
shared workspace primitives rather than legacy Halo Glass page/card components. Startup error/retry
surfaces also use the new workspace button/typography treatment. BuddyBot's joystick, emergency stop,
arming gate, forward-block behavior, STOP-on-release and SafetySupervisor authority were preserved
during migration.


## Visual regression scaffold

`HaloVisualLanguageSnapshotTest` renders a deterministic 390dp workspace contract in both light
and dark HaloTheme variants using Robolectric native graphics and Roborazzi. The fixture exercises
Halo Presence, signal glyphs, workspace cards/rows/buttons, semantic warning/error treatment and
typography without booting the runtime graph.

`AppearancePreferencesTest` verifies System as the default and persistence of an explicit theme.
The tests are committed, but no golden-image verification is claimed in this source-only pass.
Record and verify locally/CI before treating the visual acceptance checkbox as passed.


## Interaction wiring audit — 2026-09-18

Reachable production screens were traced from AppShell through their ViewModels and host callbacks.

Fixes made:
- Settings -> Permissions now receives MainActivity's real ActivityResult permission launcher; the
  grant button is no longer a no-op when the route is opened from Settings.
- PermissionsScreen requires an explicit permission-request action.
- DevicesScreen no longer accepts unused navigation/back callbacks.
- IntelligenceScreen requires a real back action and model selection has one authoritative path
  through IntelligenceViewModel.
- HeyCyan and BuddyBot detail screens require explicit back actions.
- The legacy placeholder `rippleGlass()` clickable modifier was removed because it created an
  interaction surface with no behavior.
- Intelligence Tools now edits the same persisted AiContextPolicy consumed by CognitiveSession for
  the next live turn.
- Memory is hidden because DefaultContextAssembler currently reports the memory source as unpopulated;
  exposing a switch would imply behavior that does not exist yet.
- Agent mode is hidden because the current cognitive-session path has no authoritative agent-mode
  runtime hook; unsupported controls are not presented as working features.

Current reachable-screen source audit found no empty `onClick = {}`, no required callback defaulting
to an empty action, and no screen-level TODO controls. Static rows such as About/status/telemetry
remain intentionally non-interactive and do not render interactive affordances.


## Accessibility / interaction acceptance — 2026-09-18

This pass hardened the shared interaction contract across reachable screens:

- HaloWorkspaceRow and HaloWorkspaceButton enforce a 48dp minimum touch target.
- Disabled rows no longer render a chevron and expose disabled semantics without a click action.
- Primary navigation uses selectable Tab semantics with selected-state reporting instead of raw
  clickable columns.
- Appearance choices expose RadioButton selection semantics.
- Halo Presence exposes its runtime state through accessibility semantics.
- Home composer voice controls now match runtime behavior: speech-processing disables the action,
  listening exposes Stop, and speaking exposes interrupt-and-listen behavior.
- HeyCyan media controls are enabled only when the device session is READY; transitional connection
  states no longer expose ambiguous/reentrant actions.
- BuddyBot host-backed controls are disabled when the host is unavailable.
- HeyCyan and BuddyBot auto-connect use a shared Role.Switch toggle row rather than button-like On/Off text.
- Notebook create actions and other shared interactive surfaces meet the 48dp target.
- Voice locale selection uses the shared selectable row primitive.

Regression coverage now includes workspace interaction semantics and a 1.30x font-scale Roborazzi
visual contract in addition to the light/dark snapshots.


## Real-device recovery pass — 2026-09-18

The reachable Android flows now recover from common runtime prerequisites instead of presenting
generic failure text:

- Pairing models Bluetooth prerequisites explicitly: READY, permission required, Bluetooth off,
  and unsupported BLE.
- MainActivity owns separate ActivityResult launchers for BLE permissions and the system Bluetooth
  enable request; PairingViewModel only publishes state and never attempts privileged UI actions.
- Pairing re-evaluates prerequisites on resume and before scan/connect, blocks actions while the
  prerequisite is unresolved, and maps connection transitions/failures to user-facing status.
- Microphone permission is capability-scoped rather than an application startup gate. Halo boots
  without RECORD_AUDIO; Home requests it only when the user invokes voice, and the permission
  state is re-read on foreground without restarting the process-owned HaloRuntime.
- Home refreshes AI provider diagnostics on first display and foreground resume. Confirmed
  UNCONFIGURED/OFFLINE/MODEL_MISSING/UNAVAILABLE/ERROR states render a recovery card that routes
  directly to Intelligence without disabling fallback-capable routing.
- Intelligence consumes router diagnostics, shows provider state/message, exposes Check connection,
  and restores provider-specific setup for OpenRouter API key and Ollama endpoint. Saving either
  setting refreshes diagnostics and advertised models.
- ReloadableSpeechRecognitionProvider exposes observable availability. Home routes unavailable voice
  actions to Voice setup; installing Whisper refreshes the same provider instance and updates Home
  without an app restart.
- Voice settings re-check model files on resume.

These are source-level recovery contracts. Bluetooth radio behavior, OEM permission dialogs,
network changes, actual provider reachability and physical glasses reconnect still require device
acceptance.


## Pre-burn-in lifecycle hardening — 2026-09-18

The Android recovery model is now process-owned where appropriate rather than screen-owned:

- `AndroidPlatformStateMonitor` observes Bluetooth adapter state and network transitions and
  exposes microphone permission as capability state.
- Pairing reacts to a Bluetooth off/on event while the screen remains open; it no longer depends
  only on leaving/re-entering the route.
- Home coalesces network changes before refreshing provider diagnostics.
- `DeviceAutoConnectCoordinator` owns persisted auto-connect policy. It inspects the registry
  before constructing physical hosts, so ordinary phone-only startup does not touch HeyCyan or
  BuddyBot unless a saved record explicitly opted into auto-connect.
- Enabling Auto-connect from device UI performs an immediate retry rather than waiting for restart.
- `HeyCyanHost` is now composed with the real `DeviceSessionManager`, so its saved-session and
  bounded auto-connect path is active instead of structurally disconnected.
- Pairing canonicalizes every saved glasses identity to `deviceType=HEYCYAN_GLASSES`.
  A narrow process migration repairs records produced by the earlier constructor-order bug.
- Semantic HeyCyan media controls do not use the generic raw
  `HeyCyanEmbodimentTransport.send(...)` stubs. Their source path is
  DevicesViewModel -> HeyCyanSession -> capability controller -> evidence-backed command executor
  -> HeyCyanGattCommandTransport -> GATT write.

Physical acceptance is defined in
[REAL_DEVICE_BURN_IN.md](../04-architecture/REAL_DEVICE_BURN_IN.md). Source wiring does not establish
radio/OEM behavior, reconnect timing, command completion or hardware reliability.

## Runtime fabric UI convergence — 2026-09-25

The RUNTIME-020.1 correction makes the new Runtime substrate visible through normal product surfaces:

- Home combines cognitive/speech state with durable AgentRun waiting state, so a suspended approval,
  clarification, capability or resource wait no longer appears as simply "Ready". Audio-source copy
  uses route metadata rather than treating every non-device input as a generic "Phone".
- Activity remains the durable execution/history surface and uses semantic step/action-truth copy.
- Skills resolves each action through the live `ToolDescriptor` + `ToolAvailabilityResolver` +
  capability catalogue rather than only showing raw tool IDs or a skill-level availability flag.
- Devices shows a "This host" capability summary plus per-device semantic capability/resource
  projections while keeping vendor-specific controls on detail screens.
- Settings → Runtime remains the compact status surface for tasks, capabilities and resource leases.
- Assistant compact interaction surfaces remain projections of the same durable
  `InteractionSurface` requests; sensitive compact text input is masked consistently.

This is source wiring only; visual, accessibility and physical-device acceptance is deferred to the
planned stabilization phase.

## RUNTIME-021 context and model truth — 2026-09-25

Intelligence now exposes factual model metadata (Text/Vision/Audio/Video/Tools/Streaming/context
window where advertised) rather than provider-wide capability assumptions. Runtime policy exposes
visual/workspace inclusion separately from explicit remote-sharing gates for workspace references,
current screen context and images. All remote-sharing gates default off.

These UI controls permit context use; they do not enable cloud routing by themselves. The selected
routing policy remains authoritative. This is source wiring only and has not yet received visual,
accessibility or physical-device acceptance.

## RUNTIME-057 / MINING-P0-001 local execution truth surfaces — 2026-09-30

Status: SOURCE WIRING ONLY. **BUILD NOT RUN / TESTS NOT RUN / PHONE ACCEPTANCE NOT RUN.**

**System Check** gains a "Local AI execution truth" section whose rows are independent facts, so a raw
LiteRT PASS next to a provider FAIL is readable as a layer diagnosis rather than "LiteRT broken":
Artifact, Raw LiteRT control, Halo local provider, Generation proof, Tool channel, Native resource,
Backend, Context. Actions are explicit and never chained (Run control probe / Verify / Probe tools /
Run router probe / Run agentic probe), and a `BLOCKED` control probe routes to the existing unload
path instead of silently loading a second engine. Rows may carry a compact monospace detail line
(stage, status, ms, bounded detail, exception class). Chip labels name the layer: "RUN CONTROL PROBE",
"VERIFY", "PROBE TOOLS", "RUN ROUTER PROBE".

**Model Browser** adds "Direct runtime probe" as the lowest-level action, deliberately worded apart from
"Verify model" and "Probe tool channel". A compact truth indicator line reports Artifact / Selected /
Resident / Execution / Backend / Generation / Tools. `toolsDeclared=` marks catalog metadata as a
declaration, and the displayed Tools state is the honest qualification, not the catalog boolean.

**Intelligence** adds a concise "On-device runtime" card (model, Resident · backend, native-tool
qualification) and a "Last local failure" card with stage and time only; stage timings stay in System
Check so this screen does not become a developer console.

**Android Assistant** adds an "Audio continuity" subsection (Origin, Intended output, Actual endpoint,
Fallback, Route state) fed by the audio session coordinator's affinity flow. It is diagnostic only,
configures nothing, and shows device display labels rather than MAC addresses.

Normal user screens show semantic status; technical detail appears only on System Check and Model
Browser. No new visual language, Material demo cards or log dumps were introduced.

## RUNTIME-058 / MINING-P1-001 — capability overview and new producible tools

**Skills** opens with a "What Halo can do now" summary card (`CapabilityStatusViewModel`): agent run
deadline, tool loop guard, thermal policy, saved facts, memory search kind, semantic-search
availability, learned procedures, MCP servers/tools, barge-in mode, semantic interruption and
replyable notifications. It is a read-only summary — no probes, no connection attempts — and it says
"Not installed"/"Off" where that is the truth rather than implying readiness.

New tools are all descriptor-backed, so they appear through the existing exposure planner and System
Check Phone abilities rather than as screen-specific rows:

| Tool | Screen surface |
| --- | --- |
| `halo_create_routine` | agent-facing; the existing durable approval surface is the confirmation UI |
| `halo_propose_procedure` / `halo_save_procedure` / `halo_use_procedure` | agent-facing + Skills summary counts |
| `halo_reply_notification` / `halo_replyable_notifications` | agent-facing + summary count |
| `mcp.<server>.<tool>` | dynamic entries in the same catalogue; Integrations server/tool list and detail are wired |

Activity renders the new `AgentStep.RunGuardTriggered` as **Task stopped** with the content-free
summary (for example "Repeated the same action pattern"), never as "Runtime failure".

Not yet surfaced: an MCP servers screen (add/edit/enable/refresh/test/remove), a notifications detail
"Reply supported" row (Halo has no notification detail screen), and Voice settings rows for
interruption mode and streaming playback. Those are recorded as deferred in
[ANDROID-AGENT-VOICE-MCP-MINING.md](../engineering/ANDROID-AGENT-VOICE-MCP-MINING.md).

## RUNTIME-059 / MINING-P2-001 — structured presentation rendering

`StructuredPresentationRenderer` renders the closed presentation catalog in two densities. **FULL**
(Home/conversation) shows every node including comparisons; **COMPACT** (Android Assistant overlay)
renders Text, Heading, Caption, KeyValue, Badge, Status, Progress, BulletList, Checklist, containers
and ChoiceGroup, and degrades a comparison to "Comparison available in Halo" rather than cramming a
table into a small overlay. `InteractionSurfaceRenderer` gained the `PRESENTATION` branch and always
shows the text fallback, including when the payload's schema version is newer than the renderer.

Visual language stays Halo's: theme surfaces with a 10–14 dp radius and Halo spacing, no neon or
gradient "AI" styling, no generated-content banner. Status and checklist state carry words as well as
color, so color is never the only signal. Compose semantics are applied per node — `contentDescription`
for status and key/value pairs, `progressBarRangeInfo` for determinate progress, `stateDescription`
for checklist state, `selectableGroup` plus `selectable` for choice groups, and label-then-cells linear
order for comparisons.

Skills gained optional capability-model rows (semantic memory, smart interruption, vision, audio
understanding) that render even when nothing is installed, with copy explaining that these are
abilities rather than chat models and that Halo installs nothing without a verified artifact.

Wired after the first pass: the Home "Save to Notebook" affordance on eligible cards (the note keeps
the text fallback, the bounded payload rides in existing provenance metadata, and the note view shows
the original card read-only above an ordinary editable document), the Activity plan summary (projected
checklist plus `3 / 4 steps complete · Waiting for approval`, from journal evidence only), and the
System Check "Interaction presentation" section with its explicitly tapped static component preview.
# MINING-P3-001 — multimodal perception and capability packs (2026-09-30)

Model Browser separates supported input chips from observed Vision qualification and keeps raw artifact flags/revisions in Details. Intelligence exposes role bindings and capability-pack inspection without automatic downloads. System Check reports target, qualification, capture source, input preparation and actual dedicated-STT availability; its vision probe uses synthetic content. Home exposes pending image source/thumbnail/removal/explicit persistence; Notebook file details expose object AI access. HeyCyan exposes connected source status and explicit visual question. This is source implementation only, with no rendered/device acceptance.

## MINING-P4-001 UI source wiring — 2026-09-30

| Surface | Source behavior | Validation |
| --- | --- | --- |
| Notebook Working Set | Per-reference estimates, total estimate, effective cloud eligibility, source, Open and Remove | Source reviewed; layout/device unverified |
| Context transparency | Passive estimated window/reserves/segment totals/remaining | No inference probe |
| Home | Existing P2 journal checklist, child counts/status, explicit research Save to Notebook | Source reviewed |
| Activity | Roots with nested child role, target, status, duration, tool/evidence counts | Source reviewed |
| System Check | Passive context/payload/child summaries; explicit Collect for unreferenced payloads | No new implicit integration/model probe |
| Intelligence | Delegation and context compaction controls; existing FAST/REASONING roles | Source reviewed |

[Implementation ledger](../engineering/LONG-HORIZON-AGENT-MINING.md). No UI screenshot, build, test or device acceptance was performed in this batch.

## MINING-P5-001 — phone operator surfaces (2026-09-30)

SOURCE WIRED, device acceptance pending:

- Skills → Phone screen control: passive service, reading/action policy, sensitive-app enforcement,
  last observed app/inspectability and transaction; supported policies have human-readable labels.
- Existing full/compact InteractionSurface approvals receive app/action/target/role/region and field
  character count, never raw JSON, opaque target tokens or entered values.
- Home reuses P2 checklist/progress; Activity collapses semantic transactions and expands typed evidence.
- Android Assistant settings shows passive operator status; the live compact Assistant uses the shared
  confirmation renderer and semantic preflight payload.
- System Check adds passive operator rows/metrics plus explicit Read/Preview and sandbox navigation.
- PhoneControlSandboxScreen owns inert counter/switch/input/50-row list/dialog/details controls; opening
  it does not capture it or send it to a model.

[Detailed wiring and limitations](../engineering/SEMANTIC-PHONE-OPERATOR.md). Build/tests/device acceptance NOT RUN.

## MINING-P6-001 — proactive automation runtime (2026-09-30)

Automations is a Halo workspace screen with WHEN/IF/THEN, schema-driven simple action fields, installed-app selection, delivery, reachability, setup links and substantive reviewed save. Nested schemas are disabled in the editor. Cards show ZoneId and actual reachability; history shows action counts, outcomes and grouped events. Home pending count opens Activity; Activity projects durable delivery receipts. Notification settings include typed initiative and DND. System Check links preview-only Automation sandbox.

See [Proactive automation runtime](../engineering/PROACTIVE-AUTOMATION-RUNTIME.md).

## MINING-P7-001 — personal intelligence and reflection (2026-09-30)

P7 Notebook Memory now has Confirmed/Suggestions/Episodes with inline edit/accept/dismiss/never-again review. Existing detail shows provenance/evidence/confidence/usage/revisions. Home has one passive Inbox count except Quiet; Activity shows auxiliary reflection state; Settings adds Memory & learning and System Check exposes safe counts/policy preview. Source wired only; Compose rendering, navigation and accessibility acceptance were not run.

See [P7 implementation ledger](../engineering/PERSONAL-INTELLIGENCE-REFLECTION.md).


## LOCAL-MODEL-ROLE-002

SmolLM2 135M/360M are immutable, inspected text candidates, acquired only explicitly. Schema 25 retains exact device/artifact/runtime/config/suite role evidence. Model Roles separates configured, measured and suggested targets; explicit Use revalidates and uses existing selection authorities. On-device shows current-device history, retest and content-free copy. Evaluation-purpose hints never grant capability or choose roles. No default changes, automatic acquisition, GPU qualification or new residency owner. Two-phone comparison is pending. See docs/engineering/LOCAL-MODEL-ROLE-002.md for pins, policy and physical acceptance.
