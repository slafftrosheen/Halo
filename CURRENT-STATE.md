# Halo current state

Status: CURRENT SOURCE MAP. Reviewed 2026-09-21.
Static source review only; no fresh build, instrumented test or hardware run is claimed.

[ManualDependencyGraph](app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt) is
the process composition root. [App HaloRuntime](app/src/main/java/com/halo/app/HaloRuntime.kt)
starts the phone Engine/EventBus/Blackboard and speech components. Its source explicitly
excludes the older microkernel/ProcessManager path from normal phone startup.

| Area | Present in code | Limit |
|---|---|---|
| UI | Five primary tabs (Home, Activity, Notebook, Devices, Settings); persistent NB-01 notes/search/lifecycle flow; spatial Halo Presence; System/Light/Dark appearance; reachable controls traced to real actions; shared 48dp/accessibility contracts; Home exposes provider/STT/microphone recovery; pairing exposes Bluetooth permission/radio recovery | Notebook files/memory/conversations/Working Set are not implemented; console/lab/inspector are not AppShell routes; full TalkBack/device acceptance and frame-time performance are not established |
| Presence | Asymmetric rounded-cell renderer driven by HaloRuntimeState; no permanent idle animation; compact signal-cell glyphs in Activity/Notebook/navigation; Android animator-scale zero disables Presence transitions | No measured audio/output envelope yet; low-power behavior beyond platform animation removal is not established |
| Cognition | Engine pipeline and CognitiveSession streaming/tool rounds; persisted AiContextPolicy from Intelligence is resolved for each new default conversational turn; Tools UI controls real tool exposure | General model-to-Android capability bridge is not wired; memory context source is still unpopulated, so Memory UI is intentionally hidden; no authoritative agent-mode switch exists yet |
| Model tools | Echo and current time registered in app graph | Not general phone/device actions |
| Providers | llama.cpp, Ollama, OpenRouter inference; Android assistant handoff; Sherpa and Android TTS speech; model inventory and GGUF metadata are cached/asynchronous; Qwen3 0.6B and 1.7B are selectable validation candidates | Qwen3 Android binding/chat-template behavior is not yet physically validated or made default; credential storage is not an encrypted vault |
| Speech input | Physical HeyCyan microphone capture was validated in the supplied 2026-09-21 run; external buffers now receive Silero whole-capture analysis with labeled energy fallback and typed admission rejection before cognition | Threshold/corpus acceptance remains manual; microphone validation does not establish speaker output |
| Persistence | Room Memory plus NB-01 workspace-object/note/recovery-draft tables, local note search and lifecycle management; in-memory cognitive/conversation stores | Notebook storage is not cognitive memory; Room-backed CognitiveSession and remaining Notebook object types are not established |
| Reminders | Exact-alarm adapter with simple duration parsing | Complete CRUD, robust ambiguity, permission/reboot recovery not established |
| Notifications/routines/privacy | Named modules/contracts | Operational summarizer, full automation engine and universal network firewall not established |
| Plugins | In-process registration/capability contribution/dispatch | No security sandbox or downloadable plugin installer established |
| Devices | Persisted identities/session handling; HeyCyan/BuddyBot paths; callback-serialized GATT physically validated; public CompanionDeviceManager audio association, explicit BLE-to-audio identity mapping, bonding state and exact-address route verification are implemented; wearable TTS has a strict synthesize-to-PCM/AudioTrack prototype that verifies the active route before `TtsStarted` | HeyCyan Android audio endpoint/speaker output remains unvalidated; association/profile and strict PCM playback require the next hardware run |
| Performance | Benchmark build type, Macrobenchmark module, Baseline Profile generator, startup/frame metrics, reportFullyDrawn, debug StrictMode disk/write/network policy | No benchmark or profile generation was run in this source-only implementation pass |
| Credentials | OpenRouter key written through SharedPreferences | No application-level encryption in this storage path |
| UI verification | Robolectric/Roborazzi dependencies; light/dark/1.30x-font snapshots; appearance, interaction, Bluetooth permission mapping, platform mic-state, provider projection, canonical pairing identity, auto-connect selection/migration and reloadable-STT tests | Golden images/tests have not been executed in this source-only pass; whole-screen/device-specific coverage is incomplete |
| CI | Workflow declares lint/test/build/upload | Current success not established; detekt/ktlint configuration mismatch |

See [architecture](docs/04-architecture/ARCHITECTURE.md),
[providers](docs/ai/AI_PROVIDER_ARCHITECTURE.md),
[persistence](docs/04-architecture/DATABASE.md),
[security](docs/12-security/THREAT_MODEL.md),
[HeyCyan](docs/protocols/heycyan/IMPLEMENTATION_STATUS.md) and
[audit ledger](docs/engineering/DOCUMENTATION-AUDIT.md).

Read [release readiness](RELEASE_READINESS.md) separately. Code presence is not measured
latency, power consumption, passing tests or physical acceptance.
