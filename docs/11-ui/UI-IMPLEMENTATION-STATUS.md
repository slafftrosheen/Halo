# UI source inventory

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
