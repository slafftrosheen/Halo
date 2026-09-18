# Halo current state

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.
Static source review only; no fresh build, instrumented test or hardware run is claimed.

[ManualDependencyGraph](app/src/main/java/com/halo/app/di/ManualDependencyGraph.kt) is
the process composition root. [App HaloRuntime](app/src/main/java/com/halo/app/HaloRuntime.kt)
starts the phone Engine/EventBus/Blackboard and speech components. Its source explicitly
excludes the older microkernel/ProcessManager path from normal phone startup.

| Area | Present in code | Limit |
|---|---|---|
| UI | Five primary tabs (Home, Activity, Notebook, Devices, Settings); persistent NB-01 notes/search/lifecycle flow; spatial Halo Presence; persisted System/Light/Dark appearance mode; workspace-styled intelligence, voice, permissions, pairing, HeyCyan and BuddyBot control routes | Notebook files/memory/conversations/Working Set are not implemented; console/lab/inspector are not AppShell routes; whole-app accessibility/performance not established |
| Presence | Asymmetric rounded-cell renderer driven by HaloRuntimeState; no permanent idle animation; compact signal-cell glyphs in Activity/Notebook/navigation; Android animator-scale zero disables Presence transitions | No measured audio/output envelope yet; low-power behavior beyond platform animation removal is not established |
| Cognition | Engine pipeline and CognitiveSession streaming/tool rounds | General model-to-Android capability bridge is not wired |
| Model tools | Echo and current time registered in app graph | Not general phone/device actions |
| Providers | llama.cpp, Ollama, OpenRouter inference; Android assistant handoff; Sherpa and Android TTS speech | Support is adapter/model-specific; handoff is not inference |
| Speech input | HostGraph injects AndroidAudioSource | HeyCyanAudioSource existence does not prove glasses feed the phone speech loop |
| Persistence | Room Memory plus NB-01 workspace-object/note/recovery-draft tables, local note search and lifecycle management; in-memory cognitive/conversation stores | Notebook storage is not cognitive memory; Room-backed CognitiveSession and remaining Notebook object types are not established |
| Reminders | Exact-alarm adapter with simple duration parsing | Complete CRUD, robust ambiguity, permission/reboot recovery not established |
| Notifications/routines/privacy | Named modules/contracts | Operational summarizer, full automation engine and universal network firewall not established |
| Plugins | In-process registration/capability contribution/dispatch | No security sandbox or downloadable plugin installer established |
| Devices | Persisted identities/session handling, HeyCyan and BuddyBot paths | Hardware reliability and capability consistency need evidence |
| Credentials | OpenRouter key written through SharedPreferences | No application-level encryption in this storage path |
| UI verification | Robolectric/Roborazzi dependencies plus light/dark visual-language snapshot tests and appearance persistence test | Golden images have not been recorded/verified in this source-only pass; whole-screen/device-specific coverage is incomplete |
| CI | Workflow declares lint/test/build/upload | Current success not established; detekt/ktlint configuration mismatch |

See [architecture](docs/04-architecture/ARCHITECTURE.md),
[providers](docs/ai/AI_PROVIDER_ARCHITECTURE.md),
[persistence](docs/04-architecture/DATABASE.md),
[security](docs/12-security/THREAT_MODEL.md),
[HeyCyan](docs/protocols/heycyan/IMPLEMENTATION_STATUS.md) and
[audit ledger](docs/engineering/DOCUMENTATION-AUDIT.md).

Read [release readiness](RELEASE_READINESS.md) separately. Code presence is not measured
latency, power consumption, passing tests or physical acceptance.
